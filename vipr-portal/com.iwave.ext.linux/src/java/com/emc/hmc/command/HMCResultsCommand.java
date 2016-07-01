/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.hmc.command;

import com.iwave.ext.command.CommandException;

public abstract class HMCResultsCommand<T> extends HMCCommand {
    protected T results;

    @Override
    protected void processOutput() throws CommandException {
        parseOutput();
    }

    public T getResults() {
        return results;
    }

    public abstract void parseOutput();
}