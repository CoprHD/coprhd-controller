/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.util;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.Waiter;

/**
 * Abstract class for all DR health monitors
 */
public abstract class DrHealthMonitor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DrHealthMonitor.class);

    private static int MONITORING_INTERVAL = 60;
    
    private String name;
    private int frequencyInSecs = MONITORING_INTERVAL;
    private int initDelayInSecs;

    private boolean stopped;
    private final Waiter waiter = new Waiter();
    
    public DrHealthMonitor() {
    }
    
    @Override
    public void run() {
        log.info("Starting health monitor {}", this.name);
        waiter.sleep(TimeUnit.MILLISECONDS.convert(initDelayInSecs, TimeUnit.SECONDS));
        
        while (!stopped) {
            try {
                tick();
            } catch (Exception e) {
                //try catch exception to make sure next scheduled run can be launched.
                log.error("Error occurs when monitor standby network", e);
            }
            waiter.sleep(TimeUnit.MILLISECONDS.convert(frequencyInSecs, TimeUnit.SECONDS));
        }
        log.info("Stopped health monitor {}", this.name);
    }
    
    /**
     * Start the health monitor
     */
    public void start() {
        log.info("Start health monitor {} with frequency {}", this.name, this.frequencyInSecs);
        stopped = false;
        Thread drNetworkMonitorThread = new Thread(this);
        drNetworkMonitorThread.setName(this.name);
        drNetworkMonitorThread.start();
    }
    
    /**
     * Stop the health monitor
     */
    public void stop() {
        log.info("Stopping health monitor {}", this.name);
        stopped = true;
        wakeup();
    }
    
    /**
     * Trigger the monitor
     */
    public void wakeup() {
        waiter.wakeup();
    }
    
    /**
     * Subclass should override this method 
     */
    public abstract void tick();

    /**
     * Get name of this health monitor
     *  
     * @return name of the monitor
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name of health monitor
     * @param name 
     */
    public void setName(String name) {
        this.name = name;
    }

    public int getFrequencyInSecs() {
        return frequencyInSecs;
    }

    public void setFrequencyInSecs(int frequencyInSecs) {
        this.frequencyInSecs = frequencyInSecs;
    }

    public int getInitDelayInSecs() {
        return initDelayInSecs;
    }

    public void setInitDelayInSecs(int delayInSecs) {
        this.initDelayInSecs = delayInSecs;
    }
}
