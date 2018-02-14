/*
 * Copyright (c) 2018 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

/**
 * Command to remove fstab entry
 * @author root
 *
 */
public class RemoveFromFSTabCommand extends com.iwave.ext.linux.command.RemoveFromFSTabCommand {
    public RemoveFromFSTabCommand() {
        // HP-UX sed does not support "sed -i", so we need to use temporary files
        setCommand("sed \"/${mountPoint}/d\" /etc/fstab > /tmp/fstab.tmp && cat /tmp/fstab.tmp > /etc/fstab");
        setRunAsRoot(true);
    }
}