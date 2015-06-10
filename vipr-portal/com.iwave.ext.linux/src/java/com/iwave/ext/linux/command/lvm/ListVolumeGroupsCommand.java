/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import java.util.List;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.ext.linux.command.parser.VolumeGroupParser;
import com.iwave.ext.linux.model.VolumeGroup;

/**
 * Lists all volume groups on the linux system.
 * 
 * @author jonnymiller
 */
public class ListVolumeGroupsCommand extends LinuxResultsCommand<List<VolumeGroup>> {

    public ListVolumeGroupsCommand() {
        setCommand(CommandConstants.VGDISPLAY);
        addArgument("-v");
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        results = new VolumeGroupParser().parseVolumeGroups(getOutput().getStdout());
    }
}
