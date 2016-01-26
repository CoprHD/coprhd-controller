/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;

/**
 * class extends CountingConnectionPoolMonitor for monitoring client operations
 */
public class CustomConnectionPoolMonitor extends CountingConnectionPoolMonitor {
    private static final Logger _log = LoggerFactory.getLogger(CustomConnectionPoolMonitor.class);
    static final int BUSY_CONNECTION_THRESHOLD = 50;
    static final int SUCCESS_OP_COUNT_THRESHOLD = 5 * 1000;
    private long _monitorIntervalSeconds = 1000; // every min
    private AtomicLong prevOpFailureCount = new AtomicLong();
    private long opFailureDiff = 0;
    private AtomicLong prevOpSuccessCount = new AtomicLong();
    private long opSuccessDiff = 0;
    private ScheduledExecutorService _exe = Executors.newScheduledThreadPool(1);

    public CustomConnectionPoolMonitor(long monitorIntervalSeconds) {
        super();
        _monitorIntervalSeconds = monitorIntervalSeconds;
        if (_monitorIntervalSeconds <= 0) {
            _log.info("Connection pool stats monitoring is disabled.");
            return;
        }
        // start after 1min, run every monitorIntervalSeconds
        _exe.scheduleWithFixedDelay(new Runnable() {
            private boolean needToDumpStats() {
                boolean ret = false;
                // if failures are >0 in the lastinterval
                opFailureDiff = getOperationFailureCount() - prevOpFailureCount.get();
                if (opFailureDiff > 0) {
                    ret = true;
                }
                // if success count > SUCCESS_OP_COUNT_THRESHOLD
                opSuccessDiff = getOperationSuccessCount() - prevOpSuccessCount.get();
                if (opSuccessDiff > SUCCESS_OP_COUNT_THRESHOLD) {
                    ret = true;
                }
                // if busy connection count > BUSY_CONNECTION_THRESHOLD
                if (getNumBusyConnections() > BUSY_CONNECTION_THRESHOLD) {
                    ret = true;
                }
                return ret;
            }

            @Override
            public void run() {
                if (needToDumpStats()) {
                    _log.info("In the last {}secs: Failed ops: {}, Successful ops {}, Busy connections {}",
                            new String[] { "" + _monitorIntervalSeconds, "" + opFailureDiff, "" + opSuccessDiff,
                                    "" + getNumBusyConnections() });
                    _log.info(dumpStats());
                }
                prevOpFailureCount.set(getOperationFailureCount());
                prevOpSuccessCount.set(getOperationSuccessCount());
            }
        }, 60, _monitorIntervalSeconds, TimeUnit.SECONDS);
    }

    private String dumpStats() {
        return super.toString();
    }
    
    
}
