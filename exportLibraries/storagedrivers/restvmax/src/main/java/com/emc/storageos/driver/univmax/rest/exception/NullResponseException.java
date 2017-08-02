/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class NullResponseException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 4344627306716885275L;

    /**
     * 
     */
    public NullResponseException() {
    }

    /**
     * @param message
     */
    public NullResponseException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public NullResponseException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public NullResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public NullResponseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
