/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.QosSpecification;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.QualityOfServiceMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Test creation of QoS objects after upgrade.
 */
public class QualityOfServiceMigrationTest extends DbSimpleMigrationTestBase{

    // Virtual Pool URI
    private URI vPoolId;
    // QosSpecification parameters
    private static final String QOS_NAME = "specs-testVP";
    private static final String QOS_CONSUMER = "back-end";
    // Virtual Pool parameters
    private static final String V_POOL_LABEL = "testVP";
    private static final String V_POOL_RAID_LEVEL = "5";
    private static final String V_POOL_PROVISIONING_TYPE = "Thin";
    private static final String V_POOL_PROTOCOL = "iSCSI";
    private static final String V_POOL_DRIVE_TYPE = "Solid State Drive";
    private static final String V_POOL_SYSTEM_TYPE = "NONE";
    private static final Boolean V_POOL_MULTI_VOLUME_CONSISTENCY = false;
    private static final Boolean V_POOL_EXPENDABLE = true;
    private static final Integer V_POOL_MIN_SAN_PATHS = 1;
    private static final Integer V_POOL_MAX_SAN_PATHS = 2;
    private static final Integer V_POOL_MAX_BLOCK_MIRRORS = 0;
    private static final Integer V_POOL_PATHS_PER_INITIATOR = 1;
    private static final Integer V_POOL_MAX_SNAPSHOTS = 5;
    // Size of QoS list
    private static final Integer QOS_LIST_SIZE = 1;
    // First element on the list
    private static final Integer QOS_LIST_HEAD = 0;

    private static final String TARGET_VERSION = "2.5";
    private static final String SOURCE_VERSION = "2.4";

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.4", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new QualityOfServiceMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return SOURCE_VERSION;
    }

    @Override
    protected String getTargetVersion() {
        return TARGET_VERSION;
    }

    @Override
    protected void prepareData() throws Exception {

        StringSet stringSet = new StringSet();
        stringSet.add(V_POOL_PROTOCOL);
        StringSetMap arrayInfo = new StringSetMap();
        arrayInfo.put("system_type", V_POOL_SYSTEM_TYPE);
        arrayInfo.put("raid_level", V_POOL_RAID_LEVEL);

        vPoolId = URIUtil.createId(VirtualPool.class);
        // Create Virtual Pool with given parameters
        VirtualPool virtualPool = new VirtualPool();
        virtualPool.setId(vPoolId);
        virtualPool.setLabel(V_POOL_LABEL);
        virtualPool.setSupportedProvisioningType(V_POOL_PROVISIONING_TYPE);
        virtualPool.setProtocols(stringSet);
        virtualPool.setDriveType(V_POOL_DRIVE_TYPE);
        virtualPool.setMultivolumeConsistency(V_POOL_MULTI_VOLUME_CONSISTENCY);
        virtualPool.setExpandable(V_POOL_EXPENDABLE);
        virtualPool.setNumPaths(V_POOL_MAX_SAN_PATHS);
        virtualPool.setMinPaths(V_POOL_MIN_SAN_PATHS);
        virtualPool.setMaxNativeContinuousCopies(V_POOL_MAX_BLOCK_MIRRORS);
        virtualPool.setPathsPerInitiator(V_POOL_PATHS_PER_INITIATOR);
        virtualPool.setMaxNativeSnapshots(V_POOL_MAX_SNAPSHOTS);
        virtualPool.setArrayInfo(arrayInfo);

        // Persist Virtual Pool to DB
        _dbClient.createObject(virtualPool);
    }

    @Override
    protected void verifyResults() throws Exception {

        List<URI> qosSpecsURI = _dbClient.queryByType(QosSpecification.class, true);
        Assert.assertNotNull("List of vPool URIs should not be null", qosSpecsURI);
        List<QosSpecification> qosSpecificationList = _dbClient.queryObject(QosSpecification.class, qosSpecsURI);
        Assert.assertEquals("QosSpecification list should contain one object", new Integer(qosSpecificationList.size()), QOS_LIST_SIZE);
        QosSpecification qosSpecification = qosSpecificationList.get(QOS_LIST_HEAD);
        Assert.assertNotNull("QosSpecification should not be null", qosSpecification);

        Assert.assertEquals(vPoolId, qosSpecification.getVirtualPoolId());
        Assert.assertEquals(QOS_NAME, qosSpecification.getName());
        Assert.assertEquals(QOS_CONSUMER, qosSpecification.getConsumer());
        Assert.assertEquals(V_POOL_LABEL, qosSpecification.getLabel());
        Assert.assertEquals(V_POOL_PROVISIONING_TYPE, qosSpecification.getSpecs().get("Provisioning Type"));
        Assert.assertEquals(V_POOL_PROTOCOL, qosSpecification.getSpecs().get("Protocol"));
        Assert.assertEquals(V_POOL_DRIVE_TYPE, qosSpecification.getSpecs().get("Drive Type"));
        Assert.assertEquals(V_POOL_SYSTEM_TYPE, qosSpecification.getSpecs().get("System Type"));
        Assert.assertFalse(Boolean.valueOf(qosSpecification.getSpecs().get("Multi-Volume Consistency")));
        Assert.assertTrue(Boolean.valueOf(qosSpecification.getSpecs().get("Expendable")));
        Assert.assertEquals(V_POOL_MAX_SAN_PATHS, Integer.valueOf(qosSpecification.getSpecs().get("Maximum SAN paths")));
        Assert.assertEquals(V_POOL_MIN_SAN_PATHS, Integer.valueOf(qosSpecification.getSpecs().get("Minimum SAN paths")));
        Assert.assertEquals(V_POOL_MAX_BLOCK_MIRRORS, Integer.valueOf(qosSpecification.getSpecs().get("Maximum block mirrors")));
        Assert.assertEquals(V_POOL_PATHS_PER_INITIATOR, Integer.valueOf(qosSpecification.getSpecs().get("Paths per Initiator")));
        Assert.assertEquals(V_POOL_MAX_SNAPSHOTS, Integer.valueOf(qosSpecification.getSpecs().get("Maximum Snapshots")));

    }

}
