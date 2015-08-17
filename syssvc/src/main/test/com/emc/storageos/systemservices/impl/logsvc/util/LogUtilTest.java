/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.util;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.systemservices.impl.logsvc.LogConstants;

public class LogUtilTest {

    @Test
    public void testPermitCurrentLog() {
        long maxCount = 0;
        long logCount = 101;
        long currentLogTime = System.currentTimeMillis();
        long prevLogTime = currentLogTime - 1;
        Assert.assertTrue(LogUtil.permitCurrentLog(maxCount, logCount, currentLogTime,
                prevLogTime));

        maxCount = 100;
        prevLogTime = currentLogTime;
        Assert.assertTrue(LogUtil.permitCurrentLog(maxCount, logCount, currentLogTime,
                prevLogTime));

        prevLogTime = currentLogTime - 1;
        Assert.assertFalse(LogUtil.permitCurrentLog(maxCount, logCount, currentLogTime,
                prevLogTime));

        logCount--; // 100
        Assert.assertTrue(LogUtil.permitCurrentLog(maxCount, logCount, currentLogTime,
                prevLogTime));

        logCount--; // 99
        Assert.assertTrue(LogUtil.permitCurrentLog(maxCount, logCount, currentLogTime,
                prevLogTime));
    }

    @Test
    public void testPermitNextLogBatch() {
        long maxCount = 0;
        long logCount = 100;
        int logBatchSize = 1;
        Assert.assertTrue(LogUtil.permitNextLogBatch(maxCount, logCount, logBatchSize));

        maxCount = 101;
        Assert.assertTrue(LogUtil.permitNextLogBatch(maxCount, logCount, logBatchSize));

        maxCount = 100;
        Assert.assertFalse(LogUtil.permitNextLogBatch(maxCount, logCount, logBatchSize));

        maxCount = 99;
        Assert.assertFalse(LogUtil.permitNextLogBatch(maxCount, logCount, logBatchSize));

        logBatchSize = 5;
        maxCount = logCount + logBatchSize - LogConstants.MAXCOUNT_OVERFLOW + 1;
        Assert.assertTrue(LogUtil.permitNextLogBatch(maxCount, logCount, logBatchSize));

        maxCount--;
        Assert.assertTrue(LogUtil.permitNextLogBatch(maxCount, logCount, logBatchSize));

        maxCount--;
        Assert.assertFalse(LogUtil.permitNextLogBatch(maxCount, logCount, logBatchSize));

        logCount = 0;
        logBatchSize = (int) maxCount + LogConstants.MAXCOUNT_OVERFLOW + 1;
        Assert.assertTrue(LogUtil.permitNextLogBatch(maxCount, logCount, logBatchSize));
    }
}
