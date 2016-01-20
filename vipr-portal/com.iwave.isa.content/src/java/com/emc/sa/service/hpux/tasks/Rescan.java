/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.emc.hpux.command.InsfRescanDevicesCommand;
import com.emc.hpux.command.IoscanRescanDevicesCommand;

public class Rescan extends HpuxExecutionTask<Void> {

    @Override
    public void execute() throws Exception {
        executeCommand(new IoscanRescanDevicesCommand(), SHORT_TIMEOUT);
        executeCommand(new InsfRescanDevicesCommand(), SHORT_TIMEOUT);
    }
}