/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.Collection;

import com.iwave.ext.linux.command.RescanHBAsCommand;
import com.iwave.ext.linux.model.HBAInfo;

public class RescanHBAs extends LinuxExecutionTask<Void> {

    private Collection<HBAInfo> hbas;

    public RescanHBAs(Collection<HBAInfo> hbas) {
        this.hbas = hbas;
    }

    @Override
    public void execute() throws Exception {
        RescanHBAsCommand command = new RescanHBAsCommand();
        command.setHbas(hbas);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
