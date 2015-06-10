/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.RemoveMultiPathEntryCommand;
import com.iwave.ext.linux.model.MultiPathEntry;

public class RemoveMultipathEntry extends LinuxExecutionTask<Void> {
    private MultiPathEntry entry;

    public RemoveMultipathEntry(MultiPathEntry entry) {
        this.entry = entry;
    }

    @Override
    public void execute() throws Exception {
        RemoveMultiPathEntryCommand command = new RemoveMultiPathEntryCommand(entry);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
