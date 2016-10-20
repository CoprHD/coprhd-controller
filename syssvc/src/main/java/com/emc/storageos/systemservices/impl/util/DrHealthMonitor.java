/*
 * Copyright (c) 2016 EMC Corporation
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
    private Waiter waiter;
    
    public DrHealthMonitor() {
        waiter = new Waiter();
    }
    
    @Override
    public void run() {
        log.info("Starting DR health monitor {}", this.name);
        waiter.sleep(TimeUnit.MILLISECONDS.convert(initDelayInSecs, TimeUnit.SECONDS));
        
        while (!stopped) {
            try {
                tick();
            } catch (Exception e) {
                log.error("Error occurs in DR health monitor", e);
            }
            waiter.sleep(TimeUnit.MILLISECONDS.convert(frequencyInSecs, TimeUnit.SECONDS));
        }
        log.info("Stopped DR health monitor {}", this.name);
    }
    
    /**
     * Start the health monitor
     */
    public void start() {
        log.info("Start DR health monitor {} with frequency {}", this.name, this.frequencyInSecs);
        stopped = false;
        Thread drHealthMonitorThread = new Thread(this);
        drHealthMonitorThread.setName(this.name);
        drHealthMonitorThread.start();
    }
    
    /**
     * Stop the health monitor
     */
    public void stop() {
        log.info("Stopping DR health monitor {}", this.name);
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
     * Concrete health monitor should override this method and supply business logic
     * for health check. It is supposed to be called every frequencyInSecs
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

    /**
     * Get Schedule frequency for this monitor.
     * 
     * @return frequency in seconds
     */
    public int getFrequencyInSecs() {
        return frequencyInSecs;
    }

    /**
     * Set schedule frequency 
     * 
     * @param frequencyInSecs
     */
    public void setFrequencyInSecs(int frequencyInSecs) {
        this.frequencyInSecs = frequencyInSecs;
    }

    /**
     * Get initial delay before starting the first run
     * 
     * @return initial delay in seconds
     */
    public int getInitDelayInSecs() {
        return initDelayInSecs;
    }

    /**
     * Set initial delay for the first run
     * 
     * @param delayInSecs
     */
    public void setInitDelayInSecs(int delayInSecs) {
        this.initDelayInSecs = delayInSecs;
    }
}