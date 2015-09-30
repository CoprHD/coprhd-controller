/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.List;

import com.iwave.ext.linux.command.parser.LunInfoParser;
import com.iwave.ext.linux.model.LunInfo;

public class ListLunInfoCommand extends LinuxResultsCommand<List<LunInfo>> {

    public ListLunInfoCommand() {
        setCommand("cat /proc/scsi/scsi");
    }

    @Override
    public void parseOutput() {
        results = new LunInfoParser().parseLunInfos(getOutput().getStdout());
    }
}
