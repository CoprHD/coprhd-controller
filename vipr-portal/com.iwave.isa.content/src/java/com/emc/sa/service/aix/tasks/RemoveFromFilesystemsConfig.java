/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.emc.aix.command.RemoveFromFilesystemsCommand;

public class RemoveFromFilesystemsConfig extends AixExecutionTask<Void> {

	private String mountPoint;
	
    public RemoveFromFilesystemsConfig(String mountPoint) {
    	this.mountPoint = mountPoint;
    }

    @Override
    public void execute() throws Exception {
        RemoveFromFilesystemsCommand command = new RemoveFromFilesystemsCommand(mountPoint);
        executeCommand(command, SHORT_TIMEOUT);
    }
}