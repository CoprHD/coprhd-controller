/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class FailedCreateResourceException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1956510525864704545L;

    /**
     * 
     */
    public FailedCreateResourceException() {
    }

    /**
     * @param message
     */
    public FailedCreateResourceException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public FailedCreateResourceException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public FailedCreateResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public FailedCreateResourceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
