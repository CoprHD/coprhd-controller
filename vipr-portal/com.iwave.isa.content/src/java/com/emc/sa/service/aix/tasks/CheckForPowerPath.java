/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.iwave.ext.linux.command.powerpath.PowerPathException;
import com.iwave.ext.linux.command.powerpath.PowermtCheckRegistrationCommand;

public class CheckForPowerPath extends AixExecutionTask<Boolean> {

    @Override
    public Boolean executeTask() throws Exception {
        PowermtCheckRegistrationCommand command = new PowermtCheckRegistrationCommand();
        try {
            executeCommand(command, SHORT_TIMEOUT);
            return true;
        } catch (PowerPathException e) {
            return false;
        }
    }
}