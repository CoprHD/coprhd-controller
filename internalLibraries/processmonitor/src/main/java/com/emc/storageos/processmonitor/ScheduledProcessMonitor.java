/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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
package com.emc.storageos.processmonitor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.model.ProcessMonitorMetrics;

public class ScheduledProcessMonitor {
    private static final Logger _logger = LoggerFactory
            .getLogger(ScheduledProcessMonitor.class);
    private String _interval;
    private String _serviceName;
    private ProcessMonitorMetrics processMonitorMetrics;
    private ScheduledExecutorService _scheduledProcessMonitor = Executors
            .newScheduledThreadPool(1);

    public void setInterval(String interval) {
        _interval = interval;
    }

    public void schedule() {
        try {
            _logger.info("Process Monitor Scheduled");
            processMonitorMetrics = new ProcessMonitorMetrics();
            processMonitorMetrics.setServiceName(_serviceName); 
            _scheduledProcessMonitor.scheduleAtFixedRate(
                    new ProcessMonitor(processMonitorMetrics), 0, Long.parseLong(_interval),
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            _logger.error("Process Monitor Scheduling failed", e);
        }
    }

    public void shutdown() {
        try {
            _scheduledProcessMonitor.shutdown();
            _scheduledProcessMonitor.awaitTermination(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            // To-DO: filter it for timeout sException
            // No need to throw any exception
            _logger.error("TimeOut occured after waiting Client Threads to finish in Process Monitor");
        }
    }

    public void setServiceName(String serviceName) {
        _serviceName = serviceName;
    }

    public String getServiceName() {
        return _serviceName;
    }
}
