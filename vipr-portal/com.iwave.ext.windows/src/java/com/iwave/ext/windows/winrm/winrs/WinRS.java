/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.winrs;

import org.apache.commons.lang.text.StrBuilder;
import org.apache.log4j.Logger;

import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.windows.winrm.WinRMException;
import com.iwave.ext.windows.winrm.WinRMTarget;

public class WinRS {
    private static final Logger LOG = Logger.getLogger(WinRS.class);
    private WinRMTarget target;

    public WinRS(WinRMTarget target) {
        this.target = target;
    }

    public CommandOutput executeCommandLine(String commandLine) throws WinRMException {
        return execute("cmd", String.format("/c \"%s\"", commandLine));
    }

    public CommandOutput execute(String command, String... arguments) throws WinRMException {
        String shellId = createShell();
        try {
            String commandId = submitCommand(shellId, command, arguments);
            CommandOutput output = receiveOutput(shellId, commandId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("EXIT CODE: "+output.getExitValue());
                LOG.debug("STDOUT: "+output.getStdout());
                LOG.debug("STDERR: "+output.getStderr());
            }
            return output;
        }
        finally {
            deleteShell(shellId);
        }
    }

    /**
     * Creates a new remote shell.
     * 
     * @return the remote shell ID.
     * 
     * @throws WinRMException if an error occurs.
     */
    protected String createShell() throws WinRMException {
        String shellId = newCreateShell().execute();
        return shellId;
    }

    /**
     * Submits a command to the remote shell.
     * 
     * @param shellId the shell ID.
     * @param command the command to execute.
     * @param arguments the arguments to the command.
     * @return the command ID.
     * 
     * @throws WinRMException if an error occurs.
     */
    protected String submitCommand(String shellId, String command, String[] arguments)
            throws WinRMException {
        String commandId = newSubmitCommand(shellId, command, arguments).execute();
        return commandId;
    }

    /**
     * Receives output from the remote shell command.
     * 
     * @param shellId the shell ID.
     * @param commandId the command ID.
     * @return the command output.
     * 
     * @throws WinRMException if an error occurs.
     */
    protected CommandOutput receiveOutput(String shellId, String commandId) throws WinRMException {
        StrBuilder stdout = new StrBuilder();
        StrBuilder stderr = new StrBuilder();
        int exitCode = 0;

        int sequenceId = 0;
        boolean done = false;
        while (!done) {
            ReceiveData data = newReceiveOutput(shellId, commandId, sequenceId).execute();
            stdout.append(data.getStdout());
            stderr.append(data.getStderr());
            if (data.getExitCode() != null) {
                exitCode = data.getExitCode();
            }
            done = data.isDone();
            sequenceId++;
        }

        return new CommandOutput(stdout.toString(), stderr.toString(), exitCode);
    }

    /**
     * Deletes the remote shell.
     * 
     * @param shellId the shell ID.
     * 
     * @throws WinRMException if an error occurs.
     */
    protected void deleteShell(String shellId) throws WinRMException {
        newDeleteShell(shellId).execute();
    }

    protected CreateShellOperation newCreateShell() {
        return new CreateShellOperation(target);
    }

    protected SubmitCommandOperation newSubmitCommand(String shellId, String command,
            String[] arguments) {
        return new SubmitCommandOperation(target, shellId, command, arguments);
    }

    protected ReceiveOutputOperation newReceiveOutput(String shellId, String commandId,
            int sequenceId) {
        return new ReceiveOutputOperation(target, shellId, commandId, sequenceId);
    }

    protected DeleteShellOperation newDeleteShell(String shellId) {
        return new DeleteShellOperation(target, shellId);
    }
}
