/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.command;

public interface CommandExecutor {
    /**
     * Executes the given command.
     * 
     * @param command the command to execute.
     * @return the command output.
     * 
     * @throws CommandException if an error occurs during execution.
     */
    public CommandOutput executeCommand(Command command) throws CommandException;
}
