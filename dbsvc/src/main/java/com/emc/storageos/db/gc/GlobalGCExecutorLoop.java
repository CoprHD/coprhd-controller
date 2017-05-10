/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.gc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.client.GlobalLockItf;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.GlobalLockImpl;
import com.emc.storageos.db.client.model.GlobalLock;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.util.KeyspaceUtil;

/**
 * Class schedules and runs GC job on geodb
 */
class GlobalGCExecutorLoop extends GarbageCollectionExecutorLoop {
    private static final String GEO_DB_GC_LOCK = "GEO_DB_GC_Lock";
	private static final Logger log = LoggerFactory.getLogger(GlobalGCExecutorLoop.class);
    private static final String GeoLockName = "GeoGCLock";

    private GlobalLockItf glock = null;
    private boolean locked = false;

    public GlobalGCExecutorLoop() {
    }

    @Override
    protected boolean preGC() {
        log.info("PrepareGC for geo DB");

        dbClient.start();
        VdcUtil.setDbClient(dbClient);

        locked = false;
        try {
            String myVdcId = VdcUtil.getLocalShortVdcId();
            glock = new GlobalLockImpl((DbClientImpl) dbClient, GeoLockName, GlobalLock.GL_Mode.GL_VdcShared_MODE, 0, myVdcId);

            log.info("Set global VdcShared lock owner to {} vdcId={}", dbServiceId, myVdcId);
            glock.acquire(dbServiceId);
            locked = true;
        } catch (Exception e) {
            log.error("Failed to generate the global Geo GC lock e=", e);
        }

        log.info("Get global lock for Geo GC {}", locked);
        return locked; // get the lock
    }

    /**
     * 
     * @param clazz the GC will run on
     * @param <T>
     * @return true if we can run GC on this class
     * 
     **/
    @Override
    protected <T extends DataObject> boolean canRunGCOnClass(Class<T> clazz) {
        return KeyspaceUtil.isGlobal(clazz);
    }

    /**
     * Generate the task to run GC on a class
     * 
     * @param clazz the GC will run on
     * @return a GC task
     **/
    @Override
    protected <T extends DataObject> GarbageCollectionRunnable genGCTask(Class<T> clazz) {
        return new GlobalGCRunnable(dbClient, clazz, dependencyTracker, gcDelayMins, coordinator);
    }

    @Override
    protected void postGC() {
        if (locked) {
            try {
                glock.release(dbServiceId);
                log.info("Release global lock for Geo GC");
                locked = false;
            } catch (Exception e) {
                log.error("Failed to release the global Geo lock");
            }
        }
    }
    
    @Override
	protected String getGCZKLockName() {
		return GEO_DB_GC_LOCK;
	}
}
