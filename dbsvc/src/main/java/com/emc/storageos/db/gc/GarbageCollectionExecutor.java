/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.gc;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

/**
 * Class schedules and runs GC job
 */
public class GarbageCollectionExecutor {
    // run every 10 min by default
    private static final int DEFAULT_GC_RUN_INTERVAL_MIN = 10;
    private int _gcRunInterval = DEFAULT_GC_RUN_INTERVAL_MIN;

    // Pool name
    private static final String POOL_NAME = "GCThreadPool";
    private static final Logger _log = LoggerFactory.getLogger(GarbageCollectionExecutor.class);
    private DataObjectScanner _dataObjectScanner;

    private ScheduledExecutorService _executor = new NamedScheduledThreadPoolExecutor(POOL_NAME, 1);
    private GarbageCollectionExecutorLoop gcExecutorLoop;
    private String dbServiceId;

    public GarbageCollectionExecutor() {
    }

    /**
     * Set dataObjectScanner
     * 
     * @param scanner
     */
    public void setDataObjectScanner(DataObjectScanner scanner) {
        _dataObjectScanner = scanner;

    }

    /**
     * Set for overwriting the GC run interval
     * 
     * @param mins
     */
    public void setGCRunInterval(int mins) {
        _gcRunInterval = mins;
    }

    public void setGcExecutor(GarbageCollectionExecutorLoop gcExecutor) {
        gcExecutorLoop = gcExecutor;
    }

    public void setDbServiceId(String dbServiceId) {
        this.dbServiceId = dbServiceId;
    }

    /**
     * starts the main run loop
     */
    public void start() {
        _log.info("start GC {}", gcExecutorLoop.getClass().getSimpleName());
        gcExecutorLoop.setDependencyTracker(_dataObjectScanner.getDependencyTracker());
        gcExecutorLoop.setDbServiceId(dbServiceId);
        _executor.scheduleWithFixedDelay(gcExecutorLoop, 1, _gcRunInterval, TimeUnit.MINUTES);
    }

    /**
     * Run the garbage collection loop now
     * used only from testing for now
     */
    public void runNow() {
        gcExecutorLoop.setDependencyTracker(_dataObjectScanner.getDependencyTracker());

        Future f = _executor.submit(gcExecutorLoop, 0);
        _log.info("Waiting for GC job to finish ...");
        try {
            f.get();
        } catch (ExecutionException | InterruptedException ex) {
            // log and ignore
        }
        _log.info("GC job finished.");
    }

    public void stop() {
        _executor.shutdownNow();
    }
}
