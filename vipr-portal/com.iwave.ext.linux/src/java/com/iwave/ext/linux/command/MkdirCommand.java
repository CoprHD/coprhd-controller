/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import com.iwave.ext.linux.command.LinuxCommand;

/**
 * Wraps the linux mke2fs command to format a disk.
 * 
 * @author Chris Dail
 */
public class MkdirCommand extends LinuxCommand {
    public static final String DIR = "dir";

    public MkdirCommand(boolean createParents) {
        setCommand("mkdir");
        if (createParents) {
            addArguments("-p");
        }
        addVariable(DIR);
        setRunAsRoot(true);
    }

    public void setDir(String dir) {
        setVariableValue(DIR, dir);
    }
}
