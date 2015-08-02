/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import com.iwave.ext.command.CommandException;

/**
 * This class represents a command that has results that need to be
 * processed into a T type object.
 * 
 * @param <T>
 */
public abstract class ScaleIOResultsCommand<T> extends ScaleIOCLICommand {
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
