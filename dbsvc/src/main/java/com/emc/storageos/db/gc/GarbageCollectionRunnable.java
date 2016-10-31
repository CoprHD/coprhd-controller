/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.gc;

import java.net.URI;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.db.common.DependencyTracker;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.util.TimeUUIDUtils;

/**
 * Runnable implementation for the GC threads
 */
abstract class GarbageCollectionRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(GarbageCollectionRunnable.class);
    final static String GC_LOCK_PREFIX = "gc/";
    final static long MIN_TO_MICROSECS = 60 * 1000 * 1000;
    final protected Class<? extends DataObject> type;
    final protected DbClient dbClient;
    final private long timeStartMarker;
    final protected DependencyChecker dependencyChecker;
    final private CoordinatorClient coordinator;

    GarbageCollectionRunnable(DbClient dbClient, Class<? extends DataObject> type,
            DependencyTracker dependencyTracker, int gcDelayMins,
            CoordinatorClient coordinator) {
        this.type = type;
        this.dbClient = dbClient;

        // Now - gcDelay
        if (gcDelayMins > 0) {
            timeStartMarker = TimeUUIDUtils.getMicrosTimeFromUUID(TimeUUIDUtils.getUniqueTimeUUIDinMicros())
                    - (gcDelayMins * MIN_TO_MICROSECS);
        } else {
            timeStartMarker = 0;
        }

        dependencyChecker = new DependencyChecker(dbClient, dependencyTracker);
        this.coordinator = coordinator;
    }

    /**
     * Check if a resource could be deleted from DB
     * 
     * @param id the resource ID
     * @return true if the resource an be deleted
     */
    protected abstract boolean canBeGC(URI id);

    /**
     * get list of uris to check
     * 
     * @param clazz
     */
    private URIQueryResultList getDecommissionedObjectsOfType(Class<? extends DataObject> clazz) {
        URIQueryResultList list = new URIQueryResultList();
        dbClient.queryInactiveObjects(clazz, timeStartMarker, list);
        return list;
    }

    @Override
    public void run() {
    	log.info("Starting GC loop: type: {}", type.getSimpleName());
    	
        try {
        	URIQueryResultList list = getDecommissionedObjectsOfType(type);

            int found = 0, deleted = 0;
            for (Iterator<URI> iterator = list.iterator(); iterator.hasNext();) {
                URI uri = iterator.next();
                found++;
                log.debug("GC checks dependencies for {}", uri);
                try {
                    if (!canBeGC(uri)) {
                        continue;
                    }

                    DataObject obj = dbClient.queryObject(type, uri);
                    if (obj != null) {
                        log.info("No dependencies found. Removing {}", uri);
                        dbClient.removeObject(obj);
                        deleted++;
                    }
                } catch (DatabaseException ex) {
                    log.warn("Exception from database access: ", ex);
                    // To Do - we should skip the whole loop and retry later?
                }
            }

            if (found > 0) {
                log.info(String.format("Done GC loop: type: %s, processed %s, deleted %s",
                        type.getSimpleName(), found, deleted));
            }
        } catch (Exception e) {
            log.error("Exception e=", e);
        }
    }
}
