/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.zookeeper.SingletonService;

/**
 * Singleton service to run the scheduler.
 * 
 * @author jonnymiller
 */
@Component
public class SchedulerService extends SingletonService {
    @Autowired
    private Scheduler scheduler;

    @Override
    protected void runService() {
        scheduler.run();
    }

    @Override
    protected void stopService() {
        scheduler.stop();
    }
}
