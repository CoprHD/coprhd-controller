/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import java.util.List;

import com.iwave.ext.linux.command.parser.PowerPathInquiryParser;
import com.iwave.ext.linux.model.PowerPathDevice;

public class PowerPathInquiry extends AixResultsCommand<List<PowerPathDevice>> {

    public PowerPathInquiry() {
        setCommand("inq");

        // add the display of the wwns
        addArgument("-wwn");

        // only display powerpath pseudo devices
        addArgument("-f_powerpath");

        // do not display the 'progress' dots
        addArgument("-no_dots");
    }

    @Override
    public void parseOutput() {
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            PowerPathInquiryParser parser = new PowerPathInquiryParser();
            results = parser.parseDevices(stdout);
        }
    }

}
