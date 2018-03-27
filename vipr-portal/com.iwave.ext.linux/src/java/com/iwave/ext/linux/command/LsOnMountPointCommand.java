/*
 * Copyright (c) 2017 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandException;

public class LsOnMountPointCommand extends LinuxResultsCommand<List<String>> {

	private String mountPoint;
	
    public LsOnMountPointCommand(String mountPoint) {
    	this.mountPoint = mountPoint;
        StringBuffer commandBuffer = new StringBuffer();
        commandBuffer.append("[ ! \"$(ls -A ");
        commandBuffer.append(mountPoint);
        commandBuffer.append(")\" ]");
        setCommand(commandBuffer.toString());
    }

    @SuppressWarnings("unused")
    private LsOnMountPointCommand() {

    }

    @Override
    public void parseOutput() {
        results = Lists.newArrayList();
        log.info("Ls on Mount Point execution successful. Mount point doesn't exist or doesn't contain data.");
    }
    
    
    protected void processError() throws CommandException {
        String errorMessage = "Mount point contains files or directories: " + mountPoint;
        throw new CommandException(errorMessage, getOutput());
    }
}
