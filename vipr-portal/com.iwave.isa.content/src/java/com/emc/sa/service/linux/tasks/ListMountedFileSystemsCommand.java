/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxResultsCommand;

public class ListMountedFileSystemsCommand extends LinuxResultsCommand<String> {

    public ListMountedFileSystemsCommand() {
        setCommand(CommandConstants.DF);
    }

    @Override
    public void parseOutput() {
        this.results = getOutput().getStdout();
    }

}