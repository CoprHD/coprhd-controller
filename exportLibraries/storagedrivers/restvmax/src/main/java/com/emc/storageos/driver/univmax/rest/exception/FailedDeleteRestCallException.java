/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class FailedDeleteRestCallException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -8147158245322430931L;

    /**
     * 
     */
    public FailedDeleteRestCallException() {
    }

    /**
     * @param message
     */
    public FailedDeleteRestCallException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public FailedDeleteRestCallException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public FailedDeleteRestCallException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public FailedDeleteRestCallException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
