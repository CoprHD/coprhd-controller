/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class FailedDeleteResourceException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -8147158245322430931L;

    /**
     * 
     */
    public FailedDeleteResourceException() {
    }

    /**
     * @param message
     */
    public FailedDeleteResourceException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public FailedDeleteResourceException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public FailedDeleteResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public FailedDeleteResourceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
