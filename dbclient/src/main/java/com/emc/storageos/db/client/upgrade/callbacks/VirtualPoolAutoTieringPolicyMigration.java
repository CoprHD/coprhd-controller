/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to set the "uniquePolicyNames" to true if there is a FAST policy
 * associated with the virtual pool. This is required because of a bug in 1.1 which will
 * always save the policy name irrespective of the value of uniquePolicyNames.
 * 
 */
public class VirtualPoolAutoTieringPolicyMigration extends
        BaseCustomMigrationCallback {

    private static final Logger logger = LoggerFactory.getLogger(VirtualPoolAutoTieringPolicyMigration.class);
    public static final String NATIVE_GUID_DELIMITER = "+";

    @Override
    public void process() throws MigrationCallbackException {
        logger.info("Processing virtual pool auto tiering policy migration");

        DbClient dbClient = getDbClient();
        try {
            List<URI> virtualPoolUris = dbClient.queryByType(VirtualPool.class, true);
            Iterator<VirtualPool> virtualPools = dbClient.queryIterativeObjects(VirtualPool.class, virtualPoolUris, true);

            while (virtualPools.hasNext()) {
                VirtualPool virtualPool = virtualPools.next();
                // If there is a FAST policy associated with the vpool, then mark the uniquePolicyNames to true
                if (virtualPool.getAutoTierPolicyName() != null
                        && !virtualPool.getAutoTierPolicyName().isEmpty()) {
                    // No way other than using contains to differentiate NativeGuid
                    if (virtualPool.getAutoTierPolicyName().contains(NATIVE_GUID_DELIMITER)) {
                        virtualPool.setUniquePolicyNames(false);
                    } else {
                        virtualPool.setUniquePolicyNames(true);
                    }
                    dbClient.persistObject(virtualPool);
                    logger.info("Updating VirtualPool (id={}) with  unique policy names set to {}",
                            virtualPool.getId().toString(), virtualPool.getUniquePolicyNames());
                }
            }
        } catch (Exception ex) {
            logger.error("Exception occured while migrating auto tiering policy of vpool");
            logger.error(ex.getMessage(), ex);
        }

    }

}
