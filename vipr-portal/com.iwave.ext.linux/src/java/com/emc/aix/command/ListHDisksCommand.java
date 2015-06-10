/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import java.util.List;

import com.iwave.ext.linux.command.parser.PowerPathInquiryParser;
import com.iwave.ext.linux.command.parser.PowerPathInvistaInquiryParser;
import com.iwave.ext.linux.model.PowerPathDevice;

public class ListHDisksCommand extends AixResultsCommand<List<PowerPathDevice>> {
    
    private boolean checkVplex;
    
    public static void main(String[] args) {
        new ListHDisksCommand(false, false);
    }
    
    public ListHDisksCommand(boolean usePowerPath, boolean checkVplex) {
        
        this.checkVplex = checkVplex;
        
        setCommand("inq");
       
        if (checkVplex) {
            addArgument("-invista_wwn");
        } else {
            addArgument("-wwn");
        }
       
        if (usePowerPath) {
            addArgument("-f_powerpath");
        }
        
        addArgument("-no_dots");
        setRunAsRoot(true);
        
        log.info(this.getResolvedCommandLine());
    }

    @Override
    public void parseOutput() {
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            if (this.checkVplex) {
                PowerPathInvistaInquiryParser parser = new PowerPathInvistaInquiryParser();
                results = parser.parseDevices(stdout);
            } else {
                PowerPathInquiryParser parser = new PowerPathInquiryParser();
                results = parser.parseDevices(stdout);
            }
        }
    }
    
}
