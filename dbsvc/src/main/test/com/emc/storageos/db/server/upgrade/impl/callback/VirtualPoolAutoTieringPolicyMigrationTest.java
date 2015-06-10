/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VirtualPoolAutoTieringPolicyMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class VirtualPoolAutoTieringPolicyMigrationTest extends
		DbSimpleMigrationTestBase {

	private static final Logger logger = LoggerFactory.getLogger(VirtualPoolAutoTieringPolicyMigrationTest.class);
	
	@BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {

		{
            add(new VirtualPoolAutoTieringPolicyMigration());
        }});

        DbsvcTestBase.setup();
    }
	
	@Override
	protected String getSourceVersion() {
		return "1.1";
	}

	@Override
	protected String getTargetVersion() {
		return "2.0";
	}

	@Override
	protected void prepareData() throws Exception {
		logger.info("Preparing data for virtual pool auto tiering policy migration test.");
		//vpool with FAST policy set and uniquePolicyNames set to true 
		VirtualPool vpool1 = new VirtualPool();
		URI vpool1URI = URIUtil.createId(VirtualPool.class);
		vpool1.setId(vpool1URI);
		vpool1.setAutoTierPolicyName("GOLD");
		vpool1.setUniquePolicyNames(true);
		_dbClient.createObject(vpool1);
		
		//vpool with FAST policy set and uniquePolicyNames set to false
		VirtualPool vpool2 = new VirtualPool();
		URI vpool2URI = URIUtil.createId(VirtualPool.class);
		vpool2.setId(vpool2URI);
		vpool2.setAutoTierPolicyName("SILVER");
		vpool2.setUniquePolicyNames(false);
		_dbClient.createObject(vpool2);
		
		//vpool without any FAST policy set
		VirtualPool vpool3 = new VirtualPool();
		URI vpool3URI = URIUtil.createId(VirtualPool.class);
		vpool3.setId(vpool3URI);
		_dbClient.createObject(vpool3);
		
		VirtualPool vpool4 = new VirtualPool();
        URI vpool4URI = URIUtil.createId(VirtualPool.class);
        vpool4.setId(vpool4URI);
        vpool4.setAutoTierPolicyName("SYMMETRIX+1234+FASTPOLICY+SILVER");
        vpool4.setUniquePolicyNames(true);
        _dbClient.createObject(vpool4);
        
        VirtualPool vpool5 = new VirtualPool();
        URI vpool5URI = URIUtil.createId(VirtualPool.class);
        vpool5.setId(vpool5URI);
        vpool5.setAutoTierPolicyName("SYMMETRIX+1234+FASTPOLICY+SILVER");
        vpool5.setUniquePolicyNames(false);
        _dbClient.createObject(vpool5);
		
		
	}

	@Override
	protected void verifyResults() throws Exception {
		logger.info("Verifying results for virtual pool auto tiering policy migration test.");
        List<URI> vpoolUris = _dbClient.queryByType(VirtualPool.class, true);
        Iterator<VirtualPool> vpools = _dbClient.queryIterativeObjects(VirtualPool.class, vpoolUris, true);
        
        while(vpools.hasNext()) {
        	VirtualPool vpool = vpools.next();
        	if (vpool.getAutoTierPolicyName() != null
                    && !vpool.getAutoTierPolicyName().isEmpty()) {
                if (vpool.getAutoTierPolicyName().contains(VirtualPoolAutoTieringPolicyMigration.NATIVE_GUID_DELIMITER)) {
                    Assert.assertTrue(
                            "Unique policy names should be false if FAST policy nativeGuid is associated with the virtual pool",
                            !vpool.getUniquePolicyNames());
                } else {
                    Assert.assertTrue(
                            "Unique policy names should be true if FAST policy is associated with the virtual pool",
                            vpool.getUniquePolicyNames());
                }

            }
        	
        }

	}

}
