/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.exception;

/**
 * @author fengs5
 *
 */
public class FailedPostRestCallException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1956510525864704545L;

    /**
     * 
     */
    public FailedPostRestCallException() {
        // TODO Auto-generated constructor stub
    }

    /**
     * @param message
     */
    public FailedPostRestCallException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    /**
     * @param cause
     */
    public FailedPostRestCallException(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }

    /**
     * @param message
     * @param cause
     */
    public FailedPostRestCallException(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public FailedPostRestCallException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        // TODO Auto-generated constructor stub
    }

}
