/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.emc.aix.command.RescanDevicesCommand;

public class RescanDevices extends AixExecutionTask<Void> {

    public RescanDevices() {
    }

    @Override
    public void execute() throws Exception {
        RescanDevicesCommand command = new RescanDevicesCommand();
        executeCommand(command, SHORT_TIMEOUT);
    }
}