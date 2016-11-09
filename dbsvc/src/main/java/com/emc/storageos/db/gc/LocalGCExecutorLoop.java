/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Class schedules and runs GC job on local DB
 */
class LocalGCExecutorLoop extends GarbageCollectionExecutorLoop {
    private static final String LOCAL_DB_GC_LOCK = "Local_DB_GC_Lock";
	private static final Logger log = LoggerFactory.getLogger(LocalGCExecutorLoop.class);

    public LocalGCExecutorLoop() {
        super();
    }

    @Override
    protected boolean preGC() {
        return true;
    }

    /**
     * 
     * @param clazz the GC will run on
     * @param <T>
     * @return true if we can run GC on this class
     **/
    @Override
    protected <T extends DataObject> boolean canRunGCOnClass(Class<T> clazz) {
        // local GC on run on local DB
        return KeyspaceUtil.isLocal(clazz);
    }

    /**
     * Generate GC task for class
     * 
     * @param clazz the GC will run on
     * @return a GC task
     **/
    @Override
    protected <T extends DataObject> GarbageCollectionRunnable genGCTask(Class<T> clazz) {
        return new LocalGCRunnable(dbClient, clazz, dependencyTracker, gcDelayMins, coordinator);
    }

    @Override
    protected void postGC() {
    }

	@Override
	protected String getGCZKLockName() {
		return LOCAL_DB_GC_LOCK;
	}
}
