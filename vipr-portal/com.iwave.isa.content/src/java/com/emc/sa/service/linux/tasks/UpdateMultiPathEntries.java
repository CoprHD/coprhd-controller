/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.MultipathCommand;

public class UpdateMultiPathEntries extends LinuxExecutionTask<Void> {

    public UpdateMultiPathEntries() {
    }

    @Override
    public void execute() throws Exception {
        executeCommand(new MultipathCommand(), SHORT_TIMEOUT);
    }
}
