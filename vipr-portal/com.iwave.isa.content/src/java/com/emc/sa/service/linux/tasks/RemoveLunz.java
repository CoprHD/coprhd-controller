/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.command.RemoveSCSIDeviceCommand;

public class RemoveLunz extends LinuxExecutionTask<Void> {
    private String host;
    private String channel;
    private String id;

    public RemoveLunz(int host) {
        this.host = String.valueOf(host);
        this.channel = "-";
        this.id = "-";
    }

    public RemoveLunz(int host, int channel, int id) {
        this.host = String.valueOf(host);
        this.channel = String.valueOf(channel);
        this.id = String.valueOf(id);
    }

    @Override
    public void execute() throws Exception {
        try {
            executeCommand(new RemoveSCSIDeviceCommand(host, channel, id, "0"), SHORT_TIMEOUT);
        } catch (CommandException e) {
            if (e.getOutput() != null && e.getOutput().getExitValue() != 0) {
                // Ignore
            }
            else {
                throw e;
            }
        }
    }
}
