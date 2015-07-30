/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.utility.ssh;

import org.apache.commons.lang.StringUtils;

public class SSHException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Exit code returned when executing an SSH command. */
    private int exitCode = 0;

    public SSHException(Throwable t) {
        super(t);
    }

    public SSHException(String message) {
        super(message);
    }

    public SSHException(String message, Throwable t) {
        super(message, t);
    }

    public SSHException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public SSHException(SSHOutput output) {
        this(output.getExitValue(), getMessage(output));
    }

    public int getExitCode() {
        return exitCode;
    }

    public static String getMessage(SSHOutput output) {
        String message = StringUtils.trimToNull(output.getStderr());
        if (message == null) {
            message = StringUtils.trimToNull(output.getStdout());
        }
        return message;
    }
}
