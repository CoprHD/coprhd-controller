/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.winrs;

/**
 * Class to hold the data received from a single Receive call from the remote shell.
 * 
 * @author jonnymiller
 */
public class ReceiveData {
    private String stdout;
    private String stderr;
    private String commandState;
    private Integer exitCode;

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getCommandState() {
        return commandState;
    }

    public void setCommandState(String commandState) {
        this.commandState = commandState;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public boolean isDone() {
        return WinRSConstants.WINRS_COMMAND_STATE_DONE_URI.equals(commandState);
    }
}
