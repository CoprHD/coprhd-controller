/*
 * Copyright (c) 2015 EMC Corporation
 *
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VirtualPoolVNXAutoTierPolicyIdMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;

/**
 * Test upgrade of VirtualPool VNX AutoTierPolicyId format.
 * In ViPR 2.1, UI passes the VNX AutoTierPolicyName as "CLARiiON+APM00140844986+FASTPOLICY+DEFAULT_HIGHEST_AVAILABLE" to APISvc
 * and persists the same where the same has been changed in 2.2 where UI is sending just the policy name "DEFAULT_HIGHEST_AVAILABLE"
 * Hence this migration test script tests the same.
 * 
 */
public class VirtualPoolVNXAutoTierPolicyIdMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger logger = LoggerFactory.getLogger(VirtualPoolVNXAutoTierPolicyIdMigrationTest.class);

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.1", new ArrayList<BaseCustomMigrationCallback>() {

            {
                add(new VirtualPoolVNXAutoTierPolicyIdMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "2.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.2";
    }

    @Override
    protected void prepareData() throws Exception {
        logger.info("Preparing data for virtual pool auto tiering policy Id migration test.");
        // vpool with FAST policy set
        VirtualPool vpool1 = new VirtualPool();
        URI vpool1URI = URIUtil.createId(VirtualPool.class);
        vpool1.setId(vpool1URI);
        vpool1.setAutoTierPolicyName("CLARiiON+1234+FASTPOLICY+SILVER");
        _dbClient.createObject(vpool1);

    }

    @Override
    protected void verifyResults() throws Exception {
        logger.info("Verifying results for virtual pool auto tiering policyId migration test.");
        List<URI> vpoolUris = _dbClient.queryByType(VirtualPool.class, true);
        Iterator<VirtualPool> vpools = _dbClient.queryIterativeObjects(VirtualPool.class, vpoolUris, true);

        while (vpools.hasNext()) {
            VirtualPool vpool = vpools.next();
            if (vpool.getAutoTierPolicyName() != null
                    && !vpool.getAutoTierPolicyName().isEmpty()) {
                if (vpool.getAutoTierPolicyName().contains(VirtualPoolVNXAutoTierPolicyIdMigration.CLARIION_KEY)) {
                    Assert.assertTrue(
                            "VirtualPool VNX AutoTierPolicyId change is successful.",
                            vpool.getAutoTierPolicyName().equals("SILVER"));
                }
            }

        }

    }

}
