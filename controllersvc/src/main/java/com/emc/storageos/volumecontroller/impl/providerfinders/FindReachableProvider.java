/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.providerfinders;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by bibbyi1 on 3/24/2015.
 */
public class FindReachableProvider implements FindProviderStrategy {
    private static final Logger log = LoggerFactory.getLogger(FindReachableProvider.class);

    private DbClient dbClient;
    private SmisCommandHelper helper;
    private Volume target;

    public FindReachableProvider(DbClient dbClient, SmisCommandHelper helper, Volume target) {
        this.dbClient = dbClient;
        this.helper = helper;
        this.target = target;
    }

    @Override
    public StorageSystem find() {
        RemoteDirectorGroup rdfGroup = dbClient.queryObject(RemoteDirectorGroup.class, target.getSrdfGroup());
        StorageSystem rdfGroupSourceSystem = dbClient.queryObject(StorageSystem.class, rdfGroup.getSourceStorageSystemUri()); 
        
        //if source (from RDFGroup) is not reachable, then try target system.
        //Scan would have helped us to find the reachable system but we need to wait for every 10 minutes, had this code explicitly
        // to find reachable systems before invoking fail over.
        StorageSystem reachableSystem = rdfGroupSourceSystem;
        if (!helper.checkConnectionliveness(reachableSystem)) {
            StorageSystem rdfGroupTargetSystem = dbClient.queryObject(StorageSystem.class, rdfGroup.getRemoteStorageSystemUri()); 
            log.info("Source Site {} not reachable", rdfGroupSourceSystem.getActiveProviderURI());
            if (helper.checkConnectionliveness(rdfGroupTargetSystem)) {
                log.info("Target Site {} reachable", rdfGroupTargetSystem.getActiveProviderURI());
                reachableSystem = rdfGroupTargetSystem;
            } else {
                log.info("Target Site {} not reachable", rdfGroupTargetSystem.getActiveProviderURI());
                return null;
            }
        }
        log.info("Reachable System found {}", reachableSystem.getActiveProviderURI());
        return reachableSystem;
    }
}
