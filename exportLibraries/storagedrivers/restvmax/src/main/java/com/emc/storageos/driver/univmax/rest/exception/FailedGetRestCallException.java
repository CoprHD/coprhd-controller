/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class FailedGetRestCallException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -3328652553268404734L;

    /**
     * 
     */
    public FailedGetRestCallException() {
    }

    /**
     * @param message
     */
    public FailedGetRestCallException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public FailedGetRestCallException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public FailedGetRestCallException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public FailedGetRestCallException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
