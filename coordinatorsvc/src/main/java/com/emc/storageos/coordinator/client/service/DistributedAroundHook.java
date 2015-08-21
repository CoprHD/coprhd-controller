package com.emc.storageos.coordinator.client.service;

/**
 * Concrete implementations of this class are to provide before and after
 * hook logic to wrap a given {@link com.emc.storageos.coordinator.client.service.DistributedAroundHook.Action}
 * instance.
 *
 * @author Ian Bibby
 *
 * @param <T> the return type of the run method
 */
public abstract class DistributedAroundHook<T> {

    /**
     * Interface for providing arbitrary code where the run method will be wrapped by before and after hooks.
     *
     * @param <T> the return type of the run method
     */
    public interface Action<T> {
        T run();
    }

    private Action<T> action;

    /**
     * Override this method to provide before hook logic.
     */
    public abstract void before();

    /**
     * Override this method to provide after hook logic.
     */
    public abstract void after();

    /**
     * Setter method for the Action instance to run.
     *
     * @param action Action instance to run.
     */
    public void setAction(Action<T> action) {
        this.action = action;
    }

    /**
     * Immediately run the given Action instance, wrapping it with the provided before and after hooks.
     *
     * @param action Action instance to run
     * @return A value based on the return type <T>
     */
    public T run(Action<T> action) {
        if (action == null) {
            throw new IllegalArgumentException("Provided action must not be null");
        }

        try {
            before();
            return action.run();
        } finally {
            after();
        }
    }

    /**
     * Run an Action instance that was set previously set via the #setAction method.
     *
     * @return A value based on the return type <T>
     */
    public T run() {
        return run(action);
    }
}
