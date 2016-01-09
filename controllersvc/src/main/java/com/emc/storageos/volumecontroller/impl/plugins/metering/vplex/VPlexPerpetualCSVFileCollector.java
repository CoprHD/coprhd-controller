/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vplex;

import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.plugins.AccessProfile;
import com.iwave.ext.linux.LinuxSystemCLI;

/**
 * VPlexStatsCollector implementation that reads the perpetual VPlex stats file on the management station
 * and translates them into the ViPR metrics.
 */
public class VPlexPerpetualCSVFileCollector implements VPlexStatsCollector {

    public VPlexPerpetualCSVFileCollector() {
    }

    @Override
    public StringMap collect(AccessProfile accessProfile) {
        LinuxSystemCLI cli = new LinuxSystemCLI(accessProfile.getIpAddress(), accessProfile.getUserName(), accessProfile.getPassword());
        ListVPlexPerpetualCSVFileNames listDataFileNamesCmd = new ListVPlexPerpetualCSVFileNames();
        cli.executeCommand(listDataFileNamesCmd);
        return null;
    }
}
