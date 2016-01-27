/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
 * In ViPR 2.1, UI passes the VNX AutoTierPolicyName as "CLARiiON+APM00140844986+FASTPOLICY+DEFAULT_HIGHEST_AVAILABLE" to APISvc
 * and persists the same where the same has been changed in 2.2 where UI is sending just the policy name "DEFAULT_HIGHEST_AVAILABLE"
 * 
 * This migration script updates just the policyName in the VirtualPool.
 * 
 */
public class VirtualPoolVNXAutoTierPolicyIdMigration extends
        BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(VirtualPoolVNXAutoTierPolicyIdMigration.class);
    public static final String CLARIION_KEY = "CLARiiON";
    private static final String PLUS_OPERATOR = "\\+";

    /**
     * Process the BlockVirtualPools and update their AutoTieringPolicyId from .
     */
    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();

        try {
            List<URI> virtualPoolUris = dbClient.queryByType(VirtualPool.class, true);

            Iterator<VirtualPool> virtualPools = dbClient.queryIterativeObjects(VirtualPool.class, virtualPoolUris, true);
            while (virtualPools.hasNext()) {
                VirtualPool virtualPool = virtualPools.next();
                String autoTierPolicyName = virtualPool.getAutoTierPolicyName();
                // If there is a VNX FAST policy associated with the vpool, then change the format.
                if (null != autoTierPolicyName && !autoTierPolicyName.isEmpty() && autoTierPolicyName.contains(CLARIION_KEY)) {
                    String[] autoTierPolicyArray = autoTierPolicyName.split(PLUS_OPERATOR);
                    virtualPool.setAutoTierPolicyName(autoTierPolicyArray[3]);
                    dbClient.persistObject(virtualPool);
                    log.info("Updating VirtualPool (id={}) with  right VNX FAST Policy {}",
                            virtualPool.getId().toString(), virtualPool.getAutoTierPolicyName());
                }
            }
        } catch (Exception e) {
            log.error("Exception occured while updating VirtualPool VNX AutoTieringPolicy");
            log.error(e.getMessage(), e);
        }
    }

}
