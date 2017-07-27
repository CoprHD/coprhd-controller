/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

/**
 * HP-UX command for extending the filesystem to the maximum size of the device
 * 
 */
public class ExtendFilesystemCommand extends HpuxCommand {

    public ExtendFilesystemCommand(String device) {
    	setCommand(String.format("extendfs %s", device));
        setRunAsRoot(true);
    }
}