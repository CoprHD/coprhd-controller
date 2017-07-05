/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.net.URI;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;

public class ImplicitUnManagedObjectsMatcherThread implements Runnable {

    private static final Logger _log = LoggerFactory
            .getLogger(ImplicitUnManagedObjectsMatcherThread.class);

    private VirtualPool _virtualPool = null;
    private Set<URI> _srdfEnabledTargetVPools = null;
    private Set<URI> _rpEnabledTargetVPools = null;
    private DbClient _dbClient = null;
    boolean _recalcVplexVolumes = false;

    public ImplicitUnManagedObjectsMatcherThread(VirtualPool virtualPool, Set<URI> srdfEnabledTargetVPools,
            Set<URI> rpEnabledTargetVPools, DbClient dbClient, boolean recalcVplexVolumes) {
        this._virtualPool = virtualPool;
        this._srdfEnabledTargetVPools = srdfEnabledTargetVPools;
        this._rpEnabledTargetVPools = rpEnabledTargetVPools;
        this._dbClient = dbClient;
        this._recalcVplexVolumes = recalcVplexVolumes;
    }

    @Override
    public void run() {
        _log.info("Running matchVirtualPoolsWithUnManagedVolumes on its own thread.");
        ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedVolumes(
                _virtualPool, _srdfEnabledTargetVPools, _rpEnabledTargetVPools, _dbClient, _recalcVplexVolumes);
        _log.info("Finished running matchVirtualPoolsWithUnManagedVolumes on its own thread.");
    }

}
