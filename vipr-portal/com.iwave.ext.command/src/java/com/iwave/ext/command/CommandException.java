/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.command;

public class CommandException extends RuntimeException {
    private static final long serialVersionUID = -7001472376985179584L;
    private CommandOutput output;

    public CommandException() {
    }

    public CommandException(String message) {
        super(message);
    }

    public CommandException(Throwable cause) {
        super(cause);
    }

    public CommandException(CommandOutput output) {
        setOutput(output);
    }

    public CommandException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommandException(String message, CommandOutput output) {
        super(message);
        setOutput(output);
    }

    public CommandOutput getOutput() {
        return output;
    }

    public void setOutput(CommandOutput output) {
        this.output = output;
    }
}
