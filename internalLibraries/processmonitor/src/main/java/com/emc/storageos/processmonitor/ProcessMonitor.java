/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.processmonitor;
/**
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.model.ProcessMonitorMetrics;

public class ProcessMonitor implements Runnable {
    private static final Logger _logger = LoggerFactory.getLogger(ProcessMonitor.class);
    private long _megaByteConverter = 1024*1024;
   
    private static final DateTimeFormatter dateFormatter =
    		DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS z"); 
    private ProcessMonitorMetrics usageMetrics;
    public ProcessMonitor (ProcessMonitorMetrics usgMetrics ) {
        usageMetrics = usgMetrics;
    }

    @Override
    public void run() {
        try {
            extractMemoryUsage();
            _logger.info(usageMetrics.toString());
        } catch (Exception e) {
            _logger.error("Process Monitoring Collection failed :", e);
        }
    }

    public void extractMemoryUsage() {
        final Runtime runTime = Runtime.getRuntime();
        long totalMemory = runTime.totalMemory();
        long freeMemory =  runTime.freeMemory();
        long maxMemory =   runTime.maxMemory();
        long usedMemory =  totalMemory - freeMemory;
        long timeCollected = System.currentTimeMillis();
        usageMetrics.setTotalMemory(convertToMB(totalMemory));
        usageMetrics.setFreeMemory(convertToMB(freeMemory));
        usageMetrics.setMaxMemory(convertToMB(maxMemory));
        usageMetrics.setUsedMemory(convertToMB(usedMemory));
        usageMetrics.setTimeCollected(timeCollected);
        if(usageMetrics.getUsedMemory() > usageMetrics.getMaxUsedMemory()) {
        	usageMetrics.setMaxUsedMemory(usageMetrics.getUsedMemory());
        	usageMetrics.setTimeCollectedMaxUsedMemory(dateFormatter.print(timeCollected));
        }
    }

    public long convertToMB(final long value) {
        return value / _megaByteConverter;
        
    }
}
