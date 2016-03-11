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

public class DbCompactWorker implements Runnable{
    private static final Logger log = LoggerFactory.getLogger(DbCompactWorker.class);
    private int retryTimes;
    private int initialDelayInHour; 
    private int intervalInHour;
	private static ScheduledExecutorService executor = new NamedScheduledThreadPoolExecutor("DbCompact", 1);

    /*
     * start the background db major compact job which will run every day, 
     * a major compaction consolidates all existing SSTables into a single SSTable.
     * it would clean tombstones.
     * */
    public void start() {
        executor.scheduleWithFixedDelay(this, initialDelayInHour, intervalInHour, TimeUnit.HOURS);
    }

    private void runDbCompactWithRetry(String keyspace) {
        for (int i=0; i<retryTimes; i++) {
            try {
                StorageService.instance.forceKeyspaceCompaction(keyspace);
                log.info("{} db compact successfully", keyspace);
                return;
            } catch (Exception e) {
                log.error("{} db compact failed", keyspace);
            } 
        }
        
    }

    @Override
	public void run() {
        String keyspace = DatabaseDescriptor.getClusterName();
        log.info("start db compact for {}", keyspace);
        runDbCompactWithRetry(keyspace);
	}
	
    public int getRetryTimes() {
		return retryTimes;
	}

	public void setRetryTimes(int retryTimes) {
		this.retryTimes = retryTimes;
	}

	public int getInitialDelayInHour() {
		return initialDelayInHour;
	}

	public void setInitialDelayInHour(int initialDelayInHour) {
		this.initialDelayInHour = initialDelayInHour;
	}

	public int getIntervalInHour() {
		return intervalInHour;
	}

	public void setIntervalInHour(int intervalInHour) {
		this.intervalInHour = intervalInHour;
	}
}
