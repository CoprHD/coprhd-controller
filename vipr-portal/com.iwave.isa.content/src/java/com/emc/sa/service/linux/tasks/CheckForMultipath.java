/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.command.MultipathCommand;

public class CheckForMultipath extends LinuxExecutionTask<String> {

    @Override
    public String executeTask() throws Exception {
        MultipathCommand command = new MultipathCommand();
        command.addArgument("-l");

        try {
            executeCommand(command, SHORT_TIMEOUT);
            CommandOutput output = command.getOutput();
            if (output.getExitValue() != 0) {
                return getMessage("CheckForMultipath.noMultipath");
            }
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

}
