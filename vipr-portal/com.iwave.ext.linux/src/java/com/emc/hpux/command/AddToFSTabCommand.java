/*
 * Copyright (c) 2018 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

/**
 * Command to add entry to fstab file
 *
 *
 */
public class AddToFSTabCommand extends com.iwave.ext.linux.command.AddToFSTabCommand {
    public AddToFSTabCommand() {
        setCommand("echo");
        removeArgument("-e");
        setRunAsRoot(true);
    }
}