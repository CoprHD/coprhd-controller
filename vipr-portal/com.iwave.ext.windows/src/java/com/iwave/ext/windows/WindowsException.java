/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows;

/**
 * Exception for windows errors.
 * 
 * @author Chris Dail
 */
public class WindowsException extends RuntimeException {
    private static final long serialVersionUID = -2416540931759698400L;

    public WindowsException() {
    }

    public WindowsException(String message) {
        super(message);
    }

    public WindowsException(Throwable cause) {
        super(cause);
    }

    public WindowsException(String message, Throwable cause) {
        super(message, cause);
    }
}
