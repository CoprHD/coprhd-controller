/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class ServerException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -4977371905056013634L;

    /**
     * 
     */
    public ServerException() {
    }

    /**
     * @param message
     */
    public ServerException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public ServerException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public ServerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public ServerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
