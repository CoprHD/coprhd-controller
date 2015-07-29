/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.iscsi.RescanIScsiTargetsCommand;

public class RescanIScsiInitiators extends LinuxExecutionTask<Void> {

    public RescanIScsiInitiators() {
    }

    @Override
    public void execute() throws Exception {
        executeCommand(new RescanIScsiTargetsCommand(), SHORT_TIMEOUT);
    }

}
