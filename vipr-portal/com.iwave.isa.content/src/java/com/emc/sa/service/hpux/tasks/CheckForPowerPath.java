/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.iwave.ext.linux.command.powerpath.PowerPathException;
import com.iwave.ext.linux.command.powerpath.PowermtCheckRegistrationCommand;

public class CheckForPowerPath extends HpuxExecutionTask<Boolean> {

    @Override
    public Boolean executeTask() throws Exception {
        PowermtCheckRegistrationCommand command = new PowermtCheckRegistrationCommand();
        try {
            executeCommand(command, MEDIUM_TIMEOUT);
            return true;
        } catch (PowerPathException e) {
            return false;
        }
    }
}