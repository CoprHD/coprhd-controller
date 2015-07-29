/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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

import com.emc.storageos.systemservices.impl.healthmonitor.models.CPUStats;
import com.emc.vipr.model.sys.healthmonitor.DiskStats;
import com.google.common.primitives.UnsignedLong;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Map;

public class ProcStatsTest implements StatConstants {
    private static final String INVALID_PID = "0";
    private static volatile String validPID = null;

    @BeforeClass
    public static void getValidPID() throws Exception {
        File procDir = new File(PROC_DIR);
        File[] procFiles = procDir.listFiles();
        String sname;
        for (File f : procFiles) {
            validPID = f.getName().trim();
            if (validPID.equalsIgnoreCase(SELF_DIR)) {
                continue;
            }
            sname = ProcStats.getServiceName(validPID);
            if (sname != null && !sname.isEmpty() && !"monitor".equals(sname)) {
                break;
            }
        }
        Assert.assertNotNull(validPID);
    }

    @Test
    public void testCPUNumber() {
        Assert.assertTrue(ProcStats.getCPUCount() > 0);
    }

    @Test
    public void testCPUStats() {
        try {
            CPUStats cpuStats = ProcStats.getCPUStats();
            Assert.assertTrue(cpuStats.getUserMode().compareTo(UnsignedLong.ZERO) > 0);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testNegFileDesCntr() {
        Assert.assertTrue(ProcStats.getFileDescriptorCntrs(INVALID_PID) == 0);
    }

    @Test
    public void testNegServiceName() {
        try {
            ProcStats.getServiceName(INVALID_PID);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testServiceName() {
        try {
            Assert.assertNotNull(ProcStats.getServiceName(validPID));
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testDiskStats() {
        try {
            Map<String, DiskStats> diskStatsMap = ProcStats.getDiskStats();
            DiskStats diskStats;
            for (String diskId : diskStatsMap.keySet()) {
                Assert.assertTrue(ACCEPTABLE_DISK_IDS.contains(diskId));
                diskStats = diskStatsMap.get(diskId);
                Assert.assertNotNull(diskStats);
                if (diskStats.getNumberOfReads() > 0) {
                    Assert.assertTrue(diskStats.getReadTicks() > 0);
                }
                if (diskStats.getNumberOfWrites() > 0) {
                    Assert.assertTrue(diskStats.getWriteTicks() > 0);
                }
            }
        } catch (Exception e) {
            Assert.fail();
        }
    }
}
