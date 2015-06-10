/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

public class NetAppCException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private int errorNumber = -1;
    
    public NetAppCException(String message) {
        super(message);
    }

    public NetAppCException(String message, Throwable cause) {
        // add cause to message, so it appears in portal request view
        super(message + " (" + cause.getMessage() + ")", cause);
    }
    
    public NetAppCException(String message, Throwable cause, int errorNumber) {
        this(message, cause);
        this.errorNumber = errorNumber;
    }
    
    public int getErrorNumber() {
        return errorNumber;
    }

}
