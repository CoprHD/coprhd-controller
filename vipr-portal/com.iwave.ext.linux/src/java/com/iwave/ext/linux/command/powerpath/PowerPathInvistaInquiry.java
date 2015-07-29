/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.powerpath;

import java.util.List;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.ext.linux.command.parser.PowerPathInvistaInquiryParser;
import com.iwave.ext.linux.model.PowerPathDevice;

public class PowerPathInvistaInquiry extends LinuxResultsCommand<List<PowerPathDevice>> {

    public PowerPathInvistaInquiry() {
        setCommand(CommandConstants.POWERPATHINQUIRY);

        // add the display of the wwns
        addArgument("-invista_wwn");

        // only display powerpath pseudo devices
        addArgument("-f_powerpath");

        // do not display the 'progress' dots
        addArgument("-no_dots");
    }

    @Override
    public void parseOutput() {
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            PowerPathInvistaInquiryParser parser = new PowerPathInvistaInquiryParser();
            results = parser.parseDevices(stdout);
        }
    }

}
