/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class UnauthorizedException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -1373718107897523302L;

    /**
     * 
     */
    public UnauthorizedException() {
        super();
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public UnauthorizedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * @param message
     * @param cause
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     */
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public UnauthorizedException(Throwable cause) {
        super(cause);
    }

}
