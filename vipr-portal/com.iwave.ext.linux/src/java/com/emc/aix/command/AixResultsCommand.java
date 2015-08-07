/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import com.iwave.ext.command.CommandException;

public abstract class AixResultsCommand<T> extends AixCommand {
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