/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.aix.command;

import java.util.List;

import com.emc.aix.command.parse.MultiPathInquiryParser;
import com.emc.aix.model.MultiPathDevice;

public class MultiPathInquiry extends AixResultsCommand<List<MultiPathDevice>> {

    public MultiPathInquiry() {
        setCommand("inq");
        
        // add the display of the wwns
        addArgument("-wwn");
        
        // do not display the 'progress' dots
        addArgument("-no_dots");
    }

    @Override
    public void parseOutput() {
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            MultiPathInquiryParser parser = new MultiPathInquiryParser();
            results = parser.parseDevices(stdout);
        }
    }

}
