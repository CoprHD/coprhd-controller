/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.utility.ssh;

public class SSHTimeoutException extends SSHException {
    private static final long serialVersionUID = -7419968288956847673L;

    private int timeout;

    public SSHTimeoutException(String message, int timeout) {
        super(message);
    }

    public int getTimeout() {
        return timeout;
    }
}
