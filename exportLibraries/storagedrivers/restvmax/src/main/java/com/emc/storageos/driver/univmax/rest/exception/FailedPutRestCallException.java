/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class FailedPutRestCallException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -9010240675236108712L;

    /**
     * 
     */
    public FailedPutRestCallException() {
    }

    /**
     * @param message
     */
    public FailedPutRestCallException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public FailedPutRestCallException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public FailedPutRestCallException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public FailedPutRestCallException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
