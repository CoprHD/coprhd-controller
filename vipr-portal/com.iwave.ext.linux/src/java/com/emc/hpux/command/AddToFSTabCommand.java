/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

public class AddToFSTabCommand extends com.iwave.ext.linux.command.AddToFSTabCommand {
    public AddToFSTabCommand() {
        setCommand("echo");
        setRunAsRoot(true);
    }
}
