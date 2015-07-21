/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ScaleIOPoolAndStorageSystemMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Test upgrade of SIO components. In 2.0 SIO implementation assumed that
 * the StoragePools were creating thin volumes, in actuality it was creating
 * thick. SIO 1.30+ adds supports for thin provisioned volumes, so we need to
 * change all StoragePools to indicate that they support only THICK volumes.
 */
public class ScaleIOPoolAndStorageSystemMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOPoolAndStorageSystemMigrationTest.class);

    private static StorageSystem sioStorageSystem;
    private static StorageSystem otherStorageSystem;
    private static VirtualPool sioVP;
    private static VirtualPool otherVP;
    private static List<URI> storagePoolURIs = new ArrayList<>();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.0", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;

            {
                add(new ScaleIOPoolAndStorageSystemMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "2.0";
    }

    @Override
    protected String getTargetVersion() {
        return "2.1";
    }

    @Override
    protected void prepareData() throws Exception {
        createStorageSystems();
        createStoragePools();
        createVirtualPools();
    }

    @Override
    protected void verifyResults() throws Exception {
        StorageSystem checkSioStorageSystem = _dbClient.queryObject(StorageSystem.class, sioStorageSystem.getId());
        assertNotNull(checkSioStorageSystem);
        assertEquals(checkSioStorageSystem.getLabel(), checkSioStorageSystem.getNativeGuid());

        StorageSystem checkOtherStorageSystem = _dbClient.queryObject(StorageSystem.class, otherStorageSystem.getId());
        assertNotNull(checkOtherStorageSystem);

        VirtualPool checkSioVirtualPool = _dbClient.queryObject(VirtualPool.class, sioVP.getId());
        assertNotNull(checkSioVirtualPool);
        assertEquals(checkSioVirtualPool.getSupportedProvisioningType(), VirtualPool.ProvisioningType.Thick.name());

        VirtualPool checkOtherVirtualPool = _dbClient.queryObject(VirtualPool.class, otherVP.getId());
        assertNotNull(checkOtherVirtualPool);
        assertEquals(checkOtherVirtualPool.getSupportedProvisioningType(), VirtualPool.ProvisioningType.Thin.name());

        for (URI uri : storagePoolURIs) {
            StoragePool pool = _dbClient.queryObject(StoragePool.class, uri);
            assertNotNull("StoragePool is not in database ", pool);

            if (pool.getStorageDevice().equals(sioStorageSystem.getId())) {
                // Validate that migration was applied for the objects
                assertTrue(String.format("StoragePool nativeGuid is %s different from expected %s",
                                pool.getNativeGuid(), pool.getNativeId()),
                        pool.getNativeGuid().equals(pool.getNativeId()));
                assertTrue(String.format("StoragePool %s should be THICK", pool.getNativeId()),
                        pool.getSupportedResourceTypes().equals(StoragePool.SupportedResourceTypes.THICK_ONLY.name()));
                assertStoragePoolValue("maximumThickVolumeSize", 1048576, pool.getMaximumThickVolumeSize());
                assertStoragePoolValue("minimumThickVolumeSize", 1, pool.getMinimumThickVolumeSize());
                assertStoragePoolValue("maximumThinVolumeSize", 0, pool.getMaximumThinVolumeSize());
                assertStoragePoolValue("minimumThickVolumeSize", 0, pool.getMinimumThinVolumeSize());
            } else {
                assertStoragePoolValue("maximumThinVolumeSize", 999999L, pool.getMaximumThinVolumeSize());
                assertStoragePoolValue("minimumThickVolumeSize", 500L, pool.getMinimumThinVolumeSize());
            }
        }
    }

    private void assertStoragePoolValue(String name, Object expected, Object actual) {
        assertTrue(String.format("StoragePool parameter %s should be %s, but is %s", name,
                        expected.toString(), actual.toString()),
                actual.toString().equals(expected.toString()));
    }

    private void createStorageSystems() {
        sioStorageSystem = new StorageSystem();
        sioStorageSystem.setId(URIUtil.createId(StorageSystem.class));
        sioStorageSystem.setSystemType(DiscoveredDataObject.Type.scaleio.name());
        sioStorageSystem.setLabel("SCALEIO+0aa00bb00cc00dd0+PD-1");
        sioStorageSystem.setNativeGuid("SCALEIO+0aa00bb00cc00dd0+PD-1");
        sioStorageSystem.setIpAddress("10.1.123.123");
        sioStorageSystem.setModel("ScaleIO ECS");
        sioStorageSystem.setFirmwareVersion("1.20.1.22");
        _dbClient.createObject(sioStorageSystem);

        otherStorageSystem = new StorageSystem();
        otherStorageSystem.setId(URIUtil.createId(StorageSystem.class));
        otherStorageSystem.setSystemType(DiscoveredDataObject.Type.vmax.name());
        otherStorageSystem.setLabel("SYMMETRIX+0001230002300");
        otherStorageSystem.setNativeGuid("SYMMETRIX+0001230002300");
        otherStorageSystem.setIpAddress("10.1.123.124");
        otherStorageSystem.setModel("VMAX40K");
        otherStorageSystem.setFirmwareVersion("5977.22");
        _dbClient.createObject(otherStorageSystem);
    }

    private void createStoragePools() {
        String installationId = "0aa00bb00cc00dd0";
        String protectionDomain = "PD-1";
        for (int i = 0; i < 5; i++) {
            String spName = String.format("TestSIO-Pool-%d", i);
            StoragePool pool = new StoragePool();
            URI id = URIUtil.createId(StoragePool.class);
            pool.setId(id);
            pool.setPoolName(spName);
            pool.setNativeId(String.format("%s-%s-%s", installationId, protectionDomain, spName));
            pool.setNativeGuid(String.format("%s-%s", protectionDomain, spName));
            pool.setStorageDevice(sioStorageSystem.getId());
            pool.setPoolServiceType(StoragePool.PoolServiceType.block.toString());
            pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.name());
            pool.setThinVolumePreAllocationSupported(false);
            pool.addDriveTypes(Collections.singleton(StoragePool.SupportedDriveTypeValues.SATA.name()));
            StringSet copyTypes = new StringSet();
            copyTypes.add(StoragePool.CopyTypes.ASYNC.name());
            copyTypes.add(StoragePool.CopyTypes.UNSYNC_UNASSOC.name());
            pool.setSupportedCopyTypes(copyTypes);
            pool.setMaximumThinVolumeSize(1048576L);
            pool.setMinimumThinVolumeSize(1L);
            storagePoolURIs.add(id);
        }

        // Other StoragePools
        for (int i = 0; i < 5; i++) {
            String spName = String.format("TestOther-Pool-%d", i);
            StoragePool pool = new StoragePool();
            URI id = URIUtil.createId(StoragePool.class);
            pool.setId(id);
            pool.setPoolName(spName);
            pool.setNativeId(String.format("%s-%s-%s", installationId, protectionDomain, spName));
            pool.setNativeGuid(String.format("%s-%s", protectionDomain, spName));
            pool.setStorageDevice(otherStorageSystem.getId());
            pool.setPoolServiceType(StoragePool.PoolServiceType.block.toString());
            pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.name());
            pool.setThinVolumePreAllocationSupported(false);
            pool.addDriveTypes(Collections.singleton(StoragePool.SupportedDriveTypeValues.SATA.name()));
            StringSet copyTypes = new StringSet();
            copyTypes.add(StoragePool.CopyTypes.ASYNC.name());
            copyTypes.add(StoragePool.CopyTypes.UNSYNC_UNASSOC.name());
            pool.setSupportedCopyTypes(copyTypes);
            pool.setMaximumThinVolumeSize(999999L);
            pool.setMinimumThinVolumeSize(500L);
            storagePoolURIs.add(id);
        }
    }

    private void createVirtualPools() throws InstantiationException, IllegalAccessException {
        StringSet sioProtos = new StringSet();
        sioProtos.add(HostInterface.Protocol.ScaleIO.name());
        sioVP = new VirtualPool();
        sioVP.setId(URIUtil.createId(VirtualPool.class));
        sioVP.setLabel("SIO-VirtualPool");
        sioVP.addProtocols(sioProtos);
        sioVP.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        _dbClient.createObject(sioVP);

        StringSet otherProtos = new StringSet();
        otherProtos.add(HostInterface.Protocol.ScaleIO.name());
        otherVP = new VirtualPool();
        otherVP.setId(URIUtil.createId(VirtualPool.class));
        otherVP.setLabel("other-VirtualPool");
        otherVP.addProtocols(otherProtos);
        otherVP.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thin.name());
        _dbClient.createObject(otherVP);
    }
}
