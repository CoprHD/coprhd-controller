/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.jobs;

import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import com.emc.storageos.systemservices.impl.healthmonitor.NodeResourceAllocationChecker;
import com.emc.storageos.systemservices.impl.jobs.common.JobConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NodeCheckResourceAllocationScheduler implements Runnable, JobConstants {

    private static final Logger _log = LoggerFactory.getLogger(NodeCheckResourceAllocationScheduler.class);
    private static final AlertsLogger _alertsLog = AlertsLogger.getAlertsLogger();
    private static final String PREFIX = "Resource allocation: ";

    private NodeResourceAllocationChecker _checker;

    public void setChecker(NodeResourceAllocationChecker checker) {
        _checker = checker;
    }

    /**
     * Sets up the scheduler.
     */
    public NodeCheckResourceAllocationScheduler() {
        _log.info("Initializing node balance check scheduler");
        ScheduledExecutorService service = new NamedScheduledThreadPoolExecutor("NodeResourceAllocationScheduler", 1);
        service.schedule(this, SERVICE_START_LAG, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        String resourceAllocationCheckResult = _checker.getNodeResourceAllocationCheckResult();
        if (!resourceAllocationCheckResult.contains("OK")) {
            _alertsLog.warn(PREFIX + "[" + resourceAllocationCheckResult + "]");
        }

    }
}
