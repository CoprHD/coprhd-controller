/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.powerpath.PowermtCheckRegistrationCommand;

public class CheckForPowerPath extends LinuxExecutionTask<String> {

    @Override
    public String executeTask() throws Exception {
        PowermtCheckRegistrationCommand command = new PowermtCheckRegistrationCommand();
        try {
            executeCommand(command, SHORT_TIMEOUT);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
