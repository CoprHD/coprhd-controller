/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.command.MultipathCommand;
import com.iwave.ext.linux.command.MultipathException;

public class CheckForMultipath extends LinuxExecutionTask<String> {

    @Override
    public String executeTask() throws Exception {
        MultipathCommand command = new MultipathCommand();
        command.addArgument("-l");
        executeCommand(command, SHORT_TIMEOUT);

        try {
            CommandOutput output = command.getOutput();
            if (output.getExitValue() != 0) {
                return getMessage("CheckForMultipath.noMultipath");
            }
            return null;
        } catch (MultipathException e) {
            return e.getMessage();
        }
    }

}
