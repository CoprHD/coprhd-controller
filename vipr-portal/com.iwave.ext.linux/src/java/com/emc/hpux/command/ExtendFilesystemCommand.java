/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

/**
 * HP-UX command for getting the block size of the filesystem device
 * This command runs the bdf device command and gets the block size of the partition
 * 
 */
public class ExtendFilesystemCommand extends HpuxCommand {

    public ExtendFilesystemCommand(String device) {
    	setCommand(String.format("extendfs %s", device));
        setRunAsRoot(true);
    }
}