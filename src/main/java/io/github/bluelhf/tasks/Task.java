package io.github.bluelhf.tasks;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A task represents some code that can update its progress to outsiders, and returns some result.
 * Tasks can be run asynchronously, which is useful for creating parallel threads that communicate with each other.
 * <p>
 * To create tasks, use {@link Task#of(Function)}
 *
 * @see Task#of(Function)
 * @see Observable
 */
public abstract class Task<P, R> {
    protected final Delegate delegate = new Delegate();
    private final Observable<P> progress = new Observable<>();
    private final Observable<R> result = new Observable<>();
    private CompletableFuture<R> backingFuture;

    private Task() {
    }

    /**
     * Creates a new task using the delegate-result function.
     *
     * @param <P>              The type of progress the task has, used for {@link Task#setProgress(Object)}
     * @param <R>              The resulting type of the task, an instance of which the delegate-result function will return.
     * @param supplierFunction The delegate-result function, which accepts a task delegate and returns the task's result.
     * @return The created task.
     */
    public static <P, R> Task<P, R> of(Function<Task<P, R>.Delegate, R> supplierFunction) {
        return new Task<>() {
            @Override
            protected R get() {
                return supplierFunction.apply(this.delegate);
            }
        };
    }

    /**
     * Runs this task synchronously.
     *
     * @return The result of this task.
     */
    public R run() {
        R result = get();
        resolve(result);
        return result;
    }

    protected abstract R get();

    /**
     * Runs this task asynchronously using {@link ForkJoinPool#commonPool()}.
     *
     * @return The {@link CompletableFuture} used to run the task.
     */
    public CompletableFuture<R> runAsync() {
        return runAsync(null);
    }

    /**
     * Runs this task asynchronously using the given {@link Executor}
     *
     * @param executor The executor to use.
     * @return The {@link CompletableFuture} used to run the task.
     */
    public CompletableFuture<R> runAsync(Executor executor) {
        return (backingFuture = executor == null
                ? CompletableFuture.supplyAsync(this::run).exceptionally((t) -> {
                    if (result.hasBeenSet()) return result.get();
                    throw new RuntimeException(t);
                })
                : CompletableFuture.supplyAsync(this::run, executor)).exceptionally((t) -> {
                    if (result.hasBeenSet()) return result.get();
                    throw new RuntimeException(t);
                });
    }

    /**
     * Cancels the task.
     * Cancels this task if it is running asynchronously, generating an {@link InterruptedException}.
     */
    public void cancel() {
        result.set(null);
        if (backingFuture != null)
            backingFuture.completeExceptionally(new InterruptedException("Task was forcibly cancelled."));
    }

    /**
     * Returns the {@link CompletableFuture} used to run this task.
     *
     * @return The {@link CompletableFuture} used to run this task if this task is running asynchronously.
     */
    public CompletableFuture<R> getBackingFuture() {
        if (backingFuture == null) {
            if (isCompleted()) return CompletableFuture.completedFuture(getResult());
            return null;
        }

        return backingFuture;
    }

    protected void resolve(R newResult) {
        checkCompleted();
        result.set(newResult);
    }

    /**
     * Checks whether the task has completed or not.
     *
     * @return True if this task has completed, false otherwise.
     */
    public boolean isCompleted() {
        return result.hasBeenSet();
    }

    /**
     * Returns the current progress of this task.
     *
     * @return The current progress of this task.
     */
    public P getProgress() {
        return progress.get();
    }

    protected void setProgress(P newProgress) {
        checkCompleted();
        progress.set(newProgress);
    }

    /**
     * Returns the result of this task.
     *
     * @return The result of this task, or null if none is set.
     */
    public R getResult() {
        return result.get();
    }

    /**
     * Runs the given {@link Consumer} every <i>period</i> as long as the task is running, and once when the task completes.
     *
     * @param progressConsumer The consumer to run every <i>period</i>
     * @param period           How often to run the consumer.
     * @return This task
     */
    public Task<P, R> whileRunning(Consumer<P> progressConsumer, Duration period) {
        if (period.toMillis() < 1) {
            throw new IllegalArgumentException("Periods less than 1 millisecond are not allowed. Please consider using onProgress instead.");
        }
        CompletableFuture.runAsync(() -> {
            while (!progress.hasBeenSet()) {
                LockSupport.parkNanos((long) 1E+6);
            }
            // Listening to the result observable ensures that the progress consumer runs when the task completes regardless of whether this thread is parked or not.
            BiConsumer<R, R> resultConsumer = (a, b) -> progressConsumer.accept(getProgress());
            result.listen(resultConsumer, Integer.MAX_VALUE);
            while (!result.hasBeenSet()) {
                progressConsumer.accept(getProgress());
                LockSupport.parkNanos(period.toMillis() * (long) 1E+6);
            }
            result.unlisten(resultConsumer);
        });

        return this;
    }

    /**
     * Adds a progress listener to this Task. If this task is already completed, then the listener will be called immediately with duplicate progress values.
     *
     * @param progressConsumer The progress listener, accepting both the old and new value for the progress.
     * @return This task, with the new progress listener.
     */
    public Task<P, R> onProgress(BiConsumer<P, P> progressConsumer) {
        if (isCompleted()) {
            progressConsumer.accept(getProgress(), getProgress());
            return this;
        }

        progress.listen(progressConsumer, 0);
        return this;
    }

    /**
     * Adds a result listener to this Task. If this task is already completed, then the listener will be called immediately with the result.
     *
     * @param resultConsumer The result listener to add, accepting the value of the result.
     * @return This task, with the new result listener.
     */
    public Task<P, R> onResult(Consumer<R> resultConsumer) {
        if (isCompleted()) {
            resultConsumer.accept(getResult());
            return this;
        }

        result.listen((oldResult, newResult) -> resultConsumer.accept(newResult), 0);
        return this;
    }

    private void checkCompleted() {
        if (isCompleted()) throw new IllegalStateException("Task is already completed.");
    }

    /**
     * A task delegate used when creating new tasks.
     * Task delegates allow full access to the task's progress, but nothing else.
     */
    public class Delegate {
        /**
         * @return The current progress of the task this delegate represents.
         * @see Task#getProgress()
         */
        public P getProgress() {
            return Task.this.getProgress();
        }

        /**
         * Sets the progress of the task this delegate represents.
         *
         * @param newProgress The new progress.
         * @see Task#setProgress(Object)
         */
        public void setProgress(P newProgress) {
            Task.this.setProgress(newProgress);
        }
    }
}
