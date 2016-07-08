package com.emc.storageos.driver.ibmsvcdriver.command;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;

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
