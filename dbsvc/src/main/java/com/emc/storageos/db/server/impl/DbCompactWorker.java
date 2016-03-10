/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

public final class DbCompactWorker {
    private static final Logger log = LoggerFactory.getLogger(DbCompactWorker.class);
    private static final int RETRY_TIMES = 3;
    private static final int INITIAL_DELAY_IN_HOUR = 1; 
    private static final int INTERVAL_IN_HOUR = 24;
    private static ScheduledExecutorService executor = new NamedScheduledThreadPoolExecutor("DbCompact", 1);

    private DbCompactWorker() {
        
    }
    /*
     * start the background db major compact job which will run every day, 
     * a major compaction consolidates all existing SSTables into a single SSTable.
     * it would clean tombstones.
     * */
    public static void start() {
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                runDbCompact();
            }
        }, INITIAL_DELAY_IN_HOUR, INTERVAL_IN_HOUR, TimeUnit.HOURS);
    }

    private static void runDbCompact() {
        String keyspace = DatabaseDescriptor.getClusterName();
        log.info("start db compact for {}", keyspace);
        runDbCompactWithRetry(keyspace);
    }
    
    private static void runDbCompactWithRetry(String keyspace) {
        for (int i=0; i<RETRY_TIMES; i++) {
            try {
                StorageService.instance.forceKeyspaceCompaction(keyspace);
                log.info("{} db compact successfully", keyspace);
                return;
            } catch (Exception e) {
                log.error("{} db compact failed", keyspace);
            } 
        }
        
    }
}
