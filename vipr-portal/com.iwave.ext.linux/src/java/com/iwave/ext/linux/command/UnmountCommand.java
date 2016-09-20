/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

/**
 * Unmounts a directory on the linux system.
 * 
 * @author jonnymiller
 */
public class UnmountCommand extends LinuxCommand {
    private static final String PATH = "path";

    public UnmountCommand(String path) {
        this();
        setPath(path);
    }

    public UnmountCommand(Integer timeoutVal) {
        setCommand(CommandConstants.TIMEOUT + " " + timeoutVal.toString() + " " + CommandConstants.UMOUNT);
        addVariable(PATH);
        setRunAsRoot(true);
    }

    public UnmountCommand() {
        setCommand(CommandConstants.UMOUNT);
        addVariable(PATH);
        setRunAsRoot(true);
    }

    public void setPath(String path) {
        setVariableValue(PATH, path);
    }
}
