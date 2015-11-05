package com.emc.storageos.systemservices.impl.security;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class IPSecMonitor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(IPSecMonitor.class);

    public static int IPSEC_CHECK_INTERVAL = 10;  // SECONDS
    public static int IPSEC_CHECK_INITIAL_DELAY = 10;  // SECONDS

    public ScheduledExecutorService scheduledExecutorService;

    public IPSecMonitor() {
        log.info("init IPSecMonitor");
    }

    public void start() throws Exception {
        log.info("start IPSecMonitor.");
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(
                this,
                IPSEC_CHECK_INITIAL_DELAY,
                IPSEC_CHECK_INTERVAL,
                TimeUnit.SECONDS);
        log.info("scheduled IPSecMonitor.");
    }

    public void shutdown() {
        scheduledExecutorService.shutdown();
    }

    @Override
    public void run() {
        log.info("Executed at " + System.currentTimeMillis());
    }
}
