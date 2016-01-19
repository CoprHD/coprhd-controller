/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import javax.inject.Inject;

import com.emc.hpux.HpuxSystem;
import com.emc.hpux.command.HpuxCommand;
import com.emc.hpux.command.HpuxResultsCommand;
import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.linux.command.LinuxCommand;
import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.utility.ssh.SSHCommandExecutor;

public abstract class HpuxExecutionTask<T> extends ExecutionTask<T> {
    /** No timeout. */
    public static final int NO_TIMEOUT = 0;
    /** A short timeout (1 minute) for commands that should not take very long. */
    public static final int SHORT_TIMEOUT = 60;
    /** A medium timeout (10 minutes) for commands that might take a bit longer. */
    public static final int MEDIUM_TIMEOUT = 600;
    /** A long timeout (20 minutes) for commands that could take a really long time. */
    public static final int LONG_TIMEOUT = 1200;

    @Inject
    private HpuxSystem targetCLI;

    public HpuxSystem getTargetCLI() {
        return targetCLI;
    }

    protected <V> V executeCommand(HpuxResultsCommand<V> command) {
        return executeCommand(command, NO_TIMEOUT);
    }

    protected <V> V executeCommand(HpuxResultsCommand<V> command, int timeout) {
        String commandLine = command.getResolvedCommandLine();
        debug("Executing: %s", commandLine);
        setDetail(commandLine);
        command.setCommandExecutor(createCommandExecutor(timeout));
        command.execute();
        return command.getResults();
    }

    protected <V> V executeCommand(LinuxResultsCommand<V> command) {
        return executeCommand(command, NO_TIMEOUT);
    }

    protected <V> V executeCommand(LinuxResultsCommand<V> command, int timeout) {
        String commandLine = command.getResolvedCommandLine();
        debug("Executing: %s", commandLine);
        setDetail(commandLine);
        command.setCommandExecutor(createCommandExecutor(timeout));
        command.execute();
        return command.getResults();
    }

    protected void executeCommand(HpuxCommand command) {
        executeCommand(command, NO_TIMEOUT);
    }

    protected void executeCommand(HpuxCommand command, int timeout) {
        String commandLine = command.getResolvedCommandLine();
        debug("Executing: %s", commandLine);
        setDetail(commandLine);
        command.setCommandExecutor(createCommandExecutor(timeout));
        command.execute();
    }

    protected void executeCommand(LinuxCommand command) {
        executeCommand(command, NO_TIMEOUT);
    }

    protected void executeCommand(LinuxCommand command, int timeout) {
        String commandLine = command.getResolvedCommandLine();
        debug("Executing: %s", commandLine);
        setDetail(commandLine);
        command.setCommandExecutor(createCommandExecutor(timeout));
        command.execute();
    }

    protected SSHCommandExecutor createCommandExecutor(int timeout) {
        SSHCommandExecutor executor = new SSHCommandExecutor(targetCLI.getHost(), targetCLI.getPort(),
                targetCLI.getUsername(), targetCLI.getPassword());
        executor.setCommandTimeout(timeout);
        return executor;
    }

    public void setTargetSystem(HpuxSystem targetSystem) {
        this.targetCLI = targetSystem;
    }
}