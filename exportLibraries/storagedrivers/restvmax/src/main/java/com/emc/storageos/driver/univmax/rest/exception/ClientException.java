/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

public class ClientException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 3060559258810693063L;

    /**
     * 
     */
    public ClientException() {
    }

    /**
     * @param message
     */
    public ClientException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public ClientException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public ClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
