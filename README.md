# Tasks

Tasks is a simple and versatile asynchronous task handler.

It can be used to create complex task systems, and supports custom
executor services and progress objects.

Creating a task with Tasks is easy:
```java
int max = 100000;
Task<Integer, String> task = Task.of((Task<Integer, String>.Delegate delegate) -> {
    for (int i = 0; i <= max; i++) {
        delegate.setProgress(i);
        LockSupport.parkNanos((long) 1E+8); // 100ms
    }
    return "Hello, world!";
});

task.run(); // or task.runAsync() or task.runAsync(Executor)
```
This snippet creates a new Task object that returns
Integer progress objects and resolves to a String object.
When we run the task, we can access the Integer value it's on
because it calls `delegate.setProgress(i)` in its for-loop. When it completes, its getResult method will return "Hello, world!"

Tasks also have several methods for easily tracking progress and results:
- `whileRunning(Consumer<P> progressConsumer, Duration period)` causes the progress consumer to be run every _period_, with the latest progress as input.
- `onProgress(BiConsumer<P, P> progressConsumer)` works similarly to `whileRunning`, except runs every time `setProgress` is called from inside the task, with the old progress and new progress as input respectively.
- `onResult(Consumer<R> resultConsumer)` is run once when the task completes, with the result as input.

Two of these methods are shown here:
```java
Task.of((Task<Integer, String>.Delegate taskDelegate) -> {
    for (int i = 0; i <= max; i++) {
        taskDelegate.setProgress(i);
        LockSupport.parkNanos((long) 1E+8);
    }
    return "Hello, world!";
})
    .whileRunning((progress) -> System.out.print("\r" + getProgressBar(progress, max)), Duration.ofMillis(1000))
    .onResult((result) -> System.out.println("\n" + result))
    .runAsync().join(); // equivalent to run();
```