/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class FailedGetResourceException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -3328652553268404734L;

    /**
     * 
     */
    public FailedGetResourceException() {
    }

    /**
     * @param message
     */
    public FailedGetResourceException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public FailedGetResourceException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public FailedGetResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public FailedGetResourceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
