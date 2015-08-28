/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import com.iwave.ext.command.CommandException;

/**
 * Linux command that expects results in some form.
 * 
 * @author Chris Dail
 */
public abstract class LinuxResultsCommand<T> extends LinuxCommand {
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