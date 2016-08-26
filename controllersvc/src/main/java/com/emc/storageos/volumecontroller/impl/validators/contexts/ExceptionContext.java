package com.emc.storageos.volumecontroller.impl.validators.contexts;

/**
 * Interface for providing context on how to handle exceptions.
 */
public interface ExceptionContext {

    void setAllowExceptions(boolean allowExceptions);
    boolean isAllowExceptions();
}
