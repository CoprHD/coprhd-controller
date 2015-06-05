/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class DeleteDirectoryCommand extends LinuxCommand {
    public DeleteDirectoryCommand(String path, boolean force) {
        setCommand("rm");
        addArgument(force ? "-rf" : "-r");
        addArgument(path);
        setRunAsRoot(true);
    }
}
