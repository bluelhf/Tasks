package io.github.bluelhf.tasks;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * An {@link Observable} essentially acts like a normal variable, except it can have listeners that run some code every time the value changes.
 * The listeners are executed in order of priority, and priority is given when registering a listener.
 * */
public class Observable<T> {
    private T obj = null;
    private boolean set;
    private final HashMap<BiConsumer<T, T>, Integer> changeConsumers = new HashMap<>();

    /**
     * Creates a new {@link Observable} with no value.
     * */
    public Observable() {

    }

    /**
     * Creates a new {@link Observable} with the given value. Causes future calls to {@link Observable#hasBeenSet()} to return true.
     * @param object The value to give the Observable.
     * */
    public Observable(T object) {
        this.obj = object;
        set = true;
    }

    /**
     * Sets the value of this {@link Observable}. Causes future calls to {@link Observable#hasBeenSet()} to return true.
     * @param object The value to set this Observable's value to.
     * */
    public void set(T object) {
        consume(object);
        this.obj = object;
        set = true;
    }

    /**
     * Checks whether this observable has been assigned a value or not.
     * @return True if a value, even null, has been assigned to this observable.
     * */
    public boolean hasBeenSet() {
        return set;
    }

    /**
     * Returns the current value of this {@link Observable}
     * @return The current value of this {@link Observable}
     * */
    public T get() {
        return this.obj;
    }

    private void consume(T newObject) {
        Set<Map.Entry<BiConsumer<T, T>, Integer>> entrySet = changeConsumers.entrySet();
        entrySet.stream()
                .sorted((a, b) -> -1 * a.getValue().compareTo(b.getValue()))
                .forEachOrdered((entry) -> entry.getKey().accept(get(), newObject));
    }

    /**
     * Returns all of the change consumers of this {@link Observable}
     * @return A {@link Collection} of {@link BiConsumer}s, which is backed by the consumer map of this observable.
     * */
    public Collection<BiConsumer<T, T>> getConsumers() {
        return changeConsumers.keySet();
    }

    /**
     * Adds the given consumer to this observable's consumer map
     * @param changeConsumer The change consumer, accepting the old and new value for T.
     * @param priority The priority of the consumer
     * */
    public void listen(BiConsumer<T, T> changeConsumer, int priority) {
        changeConsumers.put(changeConsumer, priority);
    }

    /**
     * Removes a consumer via a reference to the consumer object
     * @param changeConsumer The consumer object to remove.
     * */
    public void unlisten(BiConsumer<T, T> changeConsumer) {
        changeConsumers.remove(changeConsumer);
    }
}
