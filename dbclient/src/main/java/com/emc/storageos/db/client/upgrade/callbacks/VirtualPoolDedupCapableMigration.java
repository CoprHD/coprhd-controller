/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to set the DedupCapable to false in all existing Block vPools.
 * DedupCapable field was introduced as identify de-duplication supported storage pools in v3.5
 * 
 */
public class VirtualPoolDedupCapableMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(VirtualPoolDedupCapableMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        logger.info("Virtual pool DedupCapable migration START");

        DbClient dbClient = getDbClient();
        try {
            List<URI> virtualPoolURIs = dbClient.queryByType(VirtualPool.class, true);
            Iterator<VirtualPool> virtualPools = dbClient.queryIterativeObjects(VirtualPool.class, virtualPoolURIs, true);
            List<VirtualPool> modifiedVpools = new ArrayList<VirtualPool>();

            while (virtualPools.hasNext()) {
                VirtualPool virtualPool = virtualPools.next();
                if (VirtualPool.Type.block.name().equals(virtualPool.getType())
                        && virtualPool.getDedupCapable() == null) {
                    virtualPool.setDedupCapable(false);
                    modifiedVpools.add(virtualPool);
                    logger.info("Updating VirtualPool (id={}) with DedupCapable set to default value 'false' ",
                            virtualPool.getId().toString());
                }
            }
            if (!modifiedVpools.isEmpty()) {
                dbClient.updateObject(modifiedVpools);
            }
        } catch (Exception ex) {
            logger.error("Exception occured while migrating DedupCapable for Virtual pools");
            logger.error(ex.getMessage(), ex);
        }
        logger.info("Virtual pool DedupCapable migration END");
    }

}
