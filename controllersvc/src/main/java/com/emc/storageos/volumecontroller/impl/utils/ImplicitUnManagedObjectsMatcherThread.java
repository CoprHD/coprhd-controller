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

    /**
     * A Runnable implementation to execute UnManagedVolume to VirtualPool matching
     * on a separate background thread, so that the caller (for example, the Virtual Pool
     * edit API) can return more quickly.
     * 
     * @param virtualPool the virtual pool being matched
     * @param srdfEnabledTargetVPools a cached Set of SRDF enabled target Virtual Pools
     * @param rpEnabledTargetVPools a cached Set of RecoverPoint enabled target Virtual Pools
     * @param dbClient a reference to the VPLEX client
     * @param recalcVplexVolumes flag indicating whether or not VPLEX volumes should be rematched
     */
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
