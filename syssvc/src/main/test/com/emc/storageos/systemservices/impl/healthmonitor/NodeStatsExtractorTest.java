/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import com.emc.vipr.model.sys.healthmonitor.DiskStats;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class NodeStatsExtractorTest extends NodeStatsExtractor {

    private static final String INVALID_PID = "0";
    private static String validPID = null;

    @BeforeClass
    public static void getValidPID() {
        File procDir = new File(PROC_DIR);
        File[] procFiles = procDir.listFiles();
        String sname = null;
        for (File f : procFiles) {
            validPID = f.getName().trim();
            if (validPID.equalsIgnoreCase(SELF_DIR)) {
                continue;
            }
            try {
                sname = ProcStats.getServiceName(validPID);
                if (sname != null && !sname.isEmpty() && !"monitor".equals(sname)) {
                    break;
                }
            } catch (Exception e) {
            }
        }
        Assert.assertNotNull(validPID);
    }

    @Test
    public void testDiskStatsWithInterval() {
        List<DiskStats> diskStatsList = getDiskStats(2);
        Assert.assertTrue(diskStatsList != null && diskStatsList.size() > 0);
    }

    @Test
    public void testDiskStatsWithoutInterval() {
        List<DiskStats> diskStatsList = getDiskStats(0);
        Assert.assertTrue(diskStatsList != null && diskStatsList.size() > 0);
    }

    @Test
    public void testNegDeltaMS() {
        double delta = getCPUTimeDeltaMS(null, null);
        Assert.assertTrue(delta == 0);
    }

    @Test
    public void testPerSec() {
        double persec = getRate(23000, 2);
        Assert.assertTrue(persec > 0);
    }

    @Test
    public void testNegPerSec() {
        double persec = getRate(23, 0);
        Assert.assertTrue(persec == 0);
    }
}
