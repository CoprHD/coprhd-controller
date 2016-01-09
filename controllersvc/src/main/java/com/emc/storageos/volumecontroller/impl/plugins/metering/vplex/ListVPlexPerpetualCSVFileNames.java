/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vplex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.iwave.ext.linux.command.LinuxResultsCommand;

/**
 * This class encapsulates calling into the VPlex management station and getting a listing of
 * perpetual system performance log file names.
 */
public class ListVPlexPerpetualCSVFileNames extends LinuxResultsCommand<List<String>> {

    // The files should have a specific name, we'll run an ls using a glob of the expected name
    public static final String LIST_VPLEX_SYS_PERF_FILENAMES_CMD = "ls -1 /var/log/VPlex/cli/*PERPETUAL_vplex_sys_perf*log";

    public ListVPlexPerpetualCSVFileNames() {
        setCommand(LIST_VPLEX_SYS_PERF_FILENAMES_CMD);
    }

    @Override
    public void parseOutput() {
        results = new ArrayList<>();
        Collections.addAll(results, getOutput().getStdout().split("\n"));
    }
}
