/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.collectors;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;

/**
 * Given a target SRDF volume, this factory will return the strategy appropriate for collecting
 * all of the associated StorageSynchronization instances.
 * 
 * Created by bibbyi1 on 4/17/2015.
 */
public class CollectorFactory {
    private DbClient dbClient;
    private SRDFUtils utils;

    public CollectorFactory(DbClient dbClient, SRDFUtils utils) {
        this.dbClient = dbClient;
        this.utils = utils;
    }

    public CollectorStrategy getCollector(Volume target, boolean allowGroupSynchronized) {
        CollectorStrategy result = null;

        if (target.hasConsistencyGroup()) {
            if (allowGroupSynchronized) {
                result = new GroupSynchronizedCollector(dbClient, utils);
            } else {
                result = new AllStorageSyncsInCGCollector(dbClient, utils);
            }
        } else {
            if (SRDFOperations.Mode.ASYNCHRONOUS.toString().equalsIgnoreCase(target.getSrdfCopyMode()) ||
                    SRDFOperations.Mode.ACTIVE.toString().equalsIgnoreCase(target.getSrdfCopyMode())) {
                result = new AllStorageSyncsInRDFGroupCollector(dbClient, utils);
            } else {
                result = new StorageSynchronizedCollector(dbClient, utils);
            }
        }

        return result;
    }

    public CollectorStrategy getSingleCollector() {
        return new StorageSynchronizedCollector(dbClient, utils);
    }
}
