/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.exceptions;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.driver.ibmsvcdriver.connection.SSHOutput;

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
