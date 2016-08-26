/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.contexts;

/**
 * Interface for providing context on how to handle exceptions.
 */
public interface ExceptionContext {

    /**
     * Set allow exceptions to true or false.
     *
     * @param allowExceptions   boolean
     */
    void setAllowExceptions(boolean allowExceptions);

    /**
     * Return true/false based on if exceptions are allowed to be thrown.
     *
     * @return  boolean
     */
    boolean isAllowExceptions();
}
