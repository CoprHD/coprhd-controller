/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.validation;

import java.net.ConnectException;
import java.net.UnknownHostException;

import org.apache.commons.lang.StringUtils;

import util.MessagesUtils;

import com.iwave.ext.command.Command;
import com.iwave.ext.command.CommandExecutor;
import com.iwave.utility.ssh.SSHCommandExecutor;

public abstract class AbstractAssetValidator {

    public static final int SSH_CONNECT_TIMEOUT = 10000;
    public static final int SSH_READ_TIMEOUT = 5000;
    public static final int SSH_COMPLETION_TIMEOUT = 5000;

    protected String getMessage(Throwable t, String hostName) {

        Throwable rootCause = getRootCause(t);
        String message = "";
        if (rootCause instanceof UnknownHostException) {
            message = MessagesUtils.get("AssetValidator.unknownHost", rootCause.getMessage());
        }
        else if (rootCause instanceof ConnectException) {
            message = MessagesUtils.get("AssetValidator.errorConnecting", rootCause.getMessage());
        }
        else {
            message = rootCause.getMessage();
        }

        if (StringUtils.isBlank(message)) {
            message = MessagesUtils.get("validation.unknown-error");
        }
        return message;
    }

    protected String getRootMessage(Throwable t) {
        return t != null ? getRootCause(t).getMessage() : null;
    }

    protected Throwable getRootCause(Throwable t) {
        Throwable rootCause = t;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    public static void setSSHTimeout(Command command) {
        setSSHTimeout(command, SSH_CONNECT_TIMEOUT, SSH_READ_TIMEOUT, SSH_COMPLETION_TIMEOUT);
    }

    public static void setSSHTimeout(Command command, int connect, int read, int complete) {
        setSSHTimeout(command.getCommandExecutor(), connect, read, complete);
    }

    public static void setSSHTimeout(CommandExecutor executor, int connect, int read, int complete) {
        if (executor instanceof SSHCommandExecutor) {
            SSHCommandExecutor sshExecutor = (SSHCommandExecutor) executor;
            sshExecutor.setConnectTimeout(connect);
            sshExecutor.setReadTimeout(connect);
            sshExecutor.setCommandTimeout(complete);
        }
    }
}
