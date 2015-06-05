/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.providerfinders;

import com.emc.storageos.db.client.DbClient;
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
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, target.getStorageController());
        Volume source = dbClient.queryObject(Volume.class, target.getSrdfParent().getURI());
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, source.getStorageController());

        //if source is not reachable, then try target
        //Scan would have helped us to find the reachable system but we need to wait for every 10 minutes, had this code explicitly
        // to find reachable systems before invoking fail over.
        StorageSystem reachableSystem = sourceSystem;
        if (!helper.checkConnectionliveness(sourceSystem)) {
            log.info("Source Site {} Not reachable",sourceSystem.getActiveProviderURI());
            if (helper.checkConnectionliveness(targetSystem)) {
                log.info("target Site {}  reachable",sourceSystem.getActiveProviderURI());
                reachableSystem = targetSystem;
            } else {
                return null;
            }
        }
        log.info("Reachable System found {}",reachableSystem.getActiveProviderURI());
        return reachableSystem;
    }
}
