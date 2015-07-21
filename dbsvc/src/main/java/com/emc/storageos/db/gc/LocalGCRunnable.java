/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.gc;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.common.DependencyTracker;

/**
 * Runnable implementation for the GC threads
 */
public class LocalGCRunnable extends GarbageCollectionRunnable {
    private static final Logger log = LoggerFactory.getLogger(LocalGCRunnable.class);

    LocalGCRunnable(DbClient dbClient, Class<? extends DataObject> type,
                    DependencyTracker tracker, int gcDelayMins,
                    CoordinatorClient coordinator) {
        super(dbClient, type, tracker, gcDelayMins, coordinator);
    }

    @Override
    protected boolean canBeGC(URI id) {
        String dependency = dependencyChecker.checkDependencies(id, type, false);

        if (dependency != null)
            log.debug("{} has dependencies {}", id, dependency);

        return  dependency ==null;
    }
}
