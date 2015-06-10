/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.command;

import java.io.Serializable;

public class CommandOutput implements Serializable {
    private static final long serialVersionUID = -4132533553597853335L;
    /** The exit value of the command. */
    private int exitValue;
    /** The contents of the standard output stream. */
    private String stdout;
    /** The contents of the standard error stream. */
    private String stderr;

    protected CommandOutput() {
    }

    public CommandOutput(String stdout, String stderr, int exitValue) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitValue = exitValue;
    }

    public int getExitValue() {
        return exitValue;
    }

    public void setExitValue(int exitValue) {
        this.exitValue = exitValue;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }
}
