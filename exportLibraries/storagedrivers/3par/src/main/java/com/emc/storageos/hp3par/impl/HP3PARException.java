/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

public class HP3PARException extends Exception {

    private static final long serialVersionUID = 3644462124868962100L;

    private int statusCode;
    private String message;

    public HP3PARException(String message) {
        super(message);
    }

    public HP3PARException(String message, Throwable cause) {
        super(message, cause);
    }

    public HP3PARException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        setStatusCode(statusCode);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
}
