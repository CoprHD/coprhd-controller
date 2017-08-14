/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class NoResourceFoundException extends Exception {

    /**
     * 
     */
    public NoResourceFoundException() {
    }

    /**
     * @param message
     */
    public NoResourceFoundException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public NoResourceFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public NoResourceFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public NoResourceFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
