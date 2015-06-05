/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

public class WinRMException extends Exception {
    private static final long serialVersionUID = -4159271986721922868L;

    public WinRMException(String message) {
        super(message);
    }

    public WinRMException(Throwable cause) {
        super(cause);
    }

    public WinRMException(String message, Throwable cause) {
        super(message, cause);
    }
}
