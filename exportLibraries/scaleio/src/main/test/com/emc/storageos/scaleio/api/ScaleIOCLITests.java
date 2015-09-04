/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import com.emc.storageos.scaleio.ScaleIOException;
import com.emc.storageos.services.util.EnvConfig;
import com.google.common.base.Joiner;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScaleIOCLITests {

    public static final String TEST_VOLUME_PREFIX = String.format("Test%s-", (int) (Math.random() * 100));
    public static final String TEST_VOLUME_NAME = generateUUID();
    public static final String TEST_POOL = "Primary";
    public static final String TEST_PROTECTION_DOMAIN = "PD-1";
    public static final String SIO_V1_3X_REGEX = ".*?1_3\\d+.*";
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String sioHostIpAddress = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.ipaddress");
    private static final String sioHostCLIUsername = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.cli.user");
    private static final String sioHostCLIPwd = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.cli.password");
    private static final String sioHostMDMUser = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.mdm.user");
    private static final String sioHostMDMPwd = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.mdm.password");

    static ScaleIOCLI scaleIOCLI;
    static String scaleIOVersionUnderTest;

    @BeforeClass
    static public void setUp() {

        scaleIOCLI = new ScaleIOCLI(sioHostIpAddress, sioHostCLIUsername, sioHostCLIPwd);
        scaleIOVersionUnderTest = scaleIOCLI.getVersion();
        if (scaleIOVersionUnderTest.matches(SIO_V1_3X_REGEX)) {
            scaleIOCLI.setMdmUsername(sioHostMDMUser);
            scaleIOCLI.setMdmPassword(sioHostMDMPwd);
        }
        scaleIOCLI.init();
    }

    @AfterClass
    static public void cleanupAnyTestVolumes() {
        ScaleIOQueryAllVolumesResult queryAllVolumesResult =
                scaleIOCLI.queryAllVolumes();

        Collection<String> volumeIds = queryAllVolumesResult.getAllVolumeIds();
        int volumeCount = volumeIds.size();

        boolean anyVolumesDeleted = false;
        for (String id : volumeIds) {
            ScaleIOAttributes attributes = queryAllVolumesResult.getScaleIOAttributesOfVolume(id);
            String name = attributes.get(ScaleIOQueryAllVolumesResult.VOLUME_NAME);
            if (name.startsWith(TEST_VOLUME_PREFIX)) {
                System.out.printf("Going to remove left over volume %s (%s)\n", name, id);
                ScaleIORemoveVolumeResult removeVolumeResult = scaleIOCLI.removeVolume(id);
                System.out.printf("Remove %s result success = %s\n", name, removeVolumeResult.isSuccess());
                anyVolumesDeleted = true;
            }
        }

        if (anyVolumesDeleted) {
            queryAllVolumesResult = scaleIOCLI.queryAllVolumes();
            volumeIds = queryAllVolumesResult.getAllVolumeIds();
            int newVolumeCount = volumeIds.size();
            System.out.printf("There are now %d volumes. Started with %d\n", newVolumeCount, volumeCount);
        }
    }

    @Test
    public void testGetVersion() {
        String version = scaleIOCLI.getVersion();
        assertTrue(version != null);
        assertTrue(version.matches("\\d+_\\d+\\.\\d+\\.\\d+"));
        System.out.printf("SIO Version is %s", version);
    }

    @Test(expected = ScaleIOException.class)
    public void testThrowsExceptionIfNotInit() {
        ScaleIOCLI cli = new ScaleIOCLI(sioHostIpAddress, sioHostCLIUsername, sioHostCLIPwd);
        cli.queryAll();
    }

    @Test
    public void testQueryAll() {
        ScaleIOQueryAllResult results = scaleIOCLI.queryAll();
        System.out.println("PD names " + Joiner.on(',').join(results.getProtectionDomainNames()));
        for (String pdName : results.getProtectionDomainNames()) {
            System.out.printf("PD %s\n  SP names %s", pdName, Joiner.on(',').join(results.getStoragePoolsForProtectionDomain(pdName)));
            for (String spName : results.getStoragePoolsForProtectionDomain(pdName)) {
                System.out.printf("\n     %s - %s", spName, results.getStoragePoolProperties(pdName, spName));
            }
            System.out.println("");
        }
        assertEquals(scaleIOVersionUnderTest, results.getProperty(ScaleIOQueryAllCommand.SCALEIO_VERSION));
    }

    @Test
    public void testAddVolume() {
        ScaleIOAddVolumeResult addVolumeResult = scaleIOCLI.addVolume(TEST_PROTECTION_DOMAIN, TEST_POOL, TEST_VOLUME_NAME, "1");
        if (addVolumeResult.isSuccess())
            System.out.println(String.format("Volume %s (%s) size %s", addVolumeResult.getName(), addVolumeResult.getId(),
                    addVolumeResult.getActualSize()));
        else
            System.out.println(String.format("FAILED! %s", addVolumeResult.errorString()));
        assertTrue(String.format("Add volume failed %s\n", addVolumeResult.errorString()), addVolumeResult.isSuccess());

        ScaleIORemoveVolumeResult removeVolumeResult = scaleIOCLI.removeVolume(addVolumeResult.getId());
        assertTrue(String.format("Remove volume failed %s\n", removeVolumeResult.errorString()),
                removeVolumeResult.isSuccess());

        if (scaleIOVersionUnderTest.matches(SIO_V1_3X_REGEX)) {
            // Thin volume provisioning (SIO 1.30+)
            addVolumeResult = scaleIOCLI.addVolume(TEST_PROTECTION_DOMAIN, TEST_POOL, TEST_VOLUME_NAME, "1", true);
            if (addVolumeResult.isSuccess())
                System.out.println(String.format("Volume %s (%s) size %s", addVolumeResult.getName(), addVolumeResult.getId(),
                        addVolumeResult.getActualSize()));
            else
                System.out.println(String.format("FAILED! %s", addVolumeResult.errorString()));
            assertTrue(String.format("Add volume failed %s\n", addVolumeResult.errorString()), addVolumeResult.isSuccess());
            assertTrue(addVolumeResult.isThinlyProvisioned());

            removeVolumeResult = scaleIOCLI.removeVolume(addVolumeResult.getId());
            assertTrue(String.format("Remove volume failed %s\n", removeVolumeResult.errorString()),
                    removeVolumeResult.isSuccess());
        }
    }

    @Test
    public void testModifyVolumeCapacity() {
        ScaleIOAddVolumeResult addVolumeResult = scaleIOCLI.addVolume(TEST_PROTECTION_DOMAIN, TEST_POOL, TEST_VOLUME_NAME, "1");
        if (addVolumeResult.isSuccess())
            System.out.println(String.format("Volume %s (%s) size %s", addVolumeResult.getName(), addVolumeResult.getId(),
                    addVolumeResult.getActualSize()));
        else
            System.out.println(String.format("FAILED! %s", addVolumeResult.errorString()));
        assertTrue(String.format("Add volume failed %s\n", addVolumeResult.errorString()), addVolumeResult.isSuccess());

        String newSize = "50";
        ScaleIOModifyVolumeCapacityResult modifyVolumeCapacityResult =
                scaleIOCLI.modifyVolumeCapacity(addVolumeResult.getId(), newSize);
        System.out.println(String.format("Volume expand success is %s", modifyVolumeCapacityResult.isSuccess()));
        assertTrue(modifyVolumeCapacityResult.isSuccess());

        long reqSize = Long.parseLong(newSize);
        long size = Long.parseLong(modifyVolumeCapacityResult.getNewCapacity());
        boolean isModulo8 = (size % 8) == 0;
        assertTrue(String.format("Size is not as expected. Requested %s, got %s", newSize,
                modifyVolumeCapacityResult.getNewCapacity()), reqSize == size || (reqSize < size && isModulo8));

        ScaleIORemoveVolumeResult removeVolumeResult = scaleIOCLI.removeVolume(addVolumeResult.getId());
        assertTrue(String.format("Remove volume failed %s\n", removeVolumeResult.errorString()),
                removeVolumeResult.isSuccess());
    }

    @Test
    public void testSnapshotVolume() {
        ScaleIOAddVolumeResult addVolumeResult = scaleIOCLI.addVolume(TEST_PROTECTION_DOMAIN, TEST_POOL, TEST_VOLUME_NAME, "1");
        if (addVolumeResult.isSuccess())
            System.out.println(String.format("Volume %s (%s) size %s", addVolumeResult.getName(), addVolumeResult.getId(),
                    addVolumeResult.getActualSize()));
        else
            System.out.println(String.format("FAILED! %s", addVolumeResult.errorString()));
        assertTrue(String.format("Add volume failed %s\n", addVolumeResult.errorString()), addVolumeResult.isSuccess());

        final String snapshotName = generateUUID();

        ScaleIOSnapshotVolumeResult snapshotVolumeResult =
                scaleIOCLI.snapshotVolume(addVolumeResult.getId(), snapshotName);
        System.out.printf("Snapshot volume success is %s\n", snapshotVolumeResult.isSuccess());
        assertTrue(String.format("Snapshot volume failed %s\n", snapshotVolumeResult.getErrorString()),
                snapshotVolumeResult.isSuccess());
        System.out.printf("Snapshot id %s, source id is %s\n", snapshotVolumeResult.getId(),
                snapshotVolumeResult.getSourceId());
        assertEquals(snapshotName, snapshotVolumeResult.getName());

        ScaleIORemoveVolumeResult removeVolumeResult = scaleIOCLI.removeVolume(addVolumeResult.getId());
        assertTrue(String.format("Remove volume failed for source volume %s\n", removeVolumeResult.errorString()),
                removeVolumeResult.isSuccess());

        removeVolumeResult = scaleIOCLI.removeVolume(snapshotVolumeResult.getId());
        assertTrue(String.format("Remove volume failed for snapshot %s\n", removeVolumeResult.errorString()),
                removeVolumeResult.isSuccess());
    }

    @Test
    public void testSnapshotConsistencyGroupVolumes() {
        List<ScaleIOAddVolumeResult> volumes = createVolumes(3);

        Map<String, String> id2snap = new HashMap<>();
        for (ScaleIOAddVolumeResult volume : volumes) {
            id2snap.put(volume.getId(), generateUUID());
        }

        ScaleIOSnapshotMultiVolumeResult multiResult = scaleIOCLI.snapshotMultiVolume(id2snap);

        assertTrue("Snapshot multivolume failed", multiResult.isSuccess());
        assertNotNull(multiResult.getConsistencyGroupId());
        assertNotNull(multiResult.getResults());
        assertEquals(3, multiResult.getResults().size());

        for (ScaleIOSnapshotVolumeResult snapshot : multiResult.getResults()) {
            assertEquals("Source/SnaphotName did not match", id2snap.get(snapshot.getSourceId()), snapshot.getName());
        }

        ScaleIORemoveConsistencyGroupSnapshotsResult removeCGResult =
                scaleIOCLI.removeConsistencyGroupSnapshot(multiResult.getConsistencyGroupId());
        assertTrue("Remove snapshot consistency group failed", removeCGResult.isSuccess());
        assertEquals(3, removeCGResult.getCount());

        removeVolumes(volumes);
    }

    @Test
    public void testQueryCluster() {
        ScaleIOQueryClusterResult clusterResult = scaleIOCLI.queryClusterCommand();
        System.out.println(String.format("mode=%s state=%s tieBreaker=%s primaryIP=%s secondaryIP=%s tieBreakerIP=%s",
                clusterResult.getClusterMode(), clusterResult.getClusterState(), clusterResult.getTieBreakerState(),
                clusterResult.getPrimaryIP(), clusterResult.getSecondaryIP(), clusterResult.getTieBreakerIP()));
    }

    @Test
    public void testQueryAllSDCs() {
        ScaleIOQueryAllSDCResult queryAllSDCResult = scaleIOCLI.queryAllSDC();
        for (String sdcId : queryAllSDCResult.getSDCIds()) {
            ScaleIOAttributes attributes = queryAllSDCResult.getClientInfoById(sdcId);
            System.out.println(String.format("SDC %s %s", sdcId, attributes.toString()));
        }
    }

    @Test
    public void testQueryAllSDSs() {
        ScaleIOQueryAllSDSResult queryAllSDSResult = scaleIOCLI.queryAllSDS();
        assertTrue("Expected 1 or more SDSs", queryAllSDSResult.getProtectionDomainIds().size() > 0);
        for (String id : queryAllSDSResult.getProtectionDomainIds()) {
            assertTrue(String.format("Protection domain ID does not look like an ID -- '%s'", id), id.length() == 16);
            String pdName = queryAllSDSResult.getProtectionDomainName(id);
            for (ScaleIOAttributes attributes : queryAllSDSResult.getSDSForProtectionDomain(id)) {
                String sdsID = attributes.get(ScaleIOQueryAllSDSResult.SDS_ID);
                assertTrue(String.format("SDS ID does not look like an ID -- '%s'", sdsID), sdsID.length() == 16);
                System.out.println(String.format("PD %s (%s) -- SDS %s", pdName, id, attributes.toString()));
            }
        }
    }

    @Test
    public void testQueryStoragePool() {
        ScaleIOQueryStoragePoolResult storagePoolResult = scaleIOCLI.queryStoragePool(TEST_PROTECTION_DOMAIN, TEST_POOL);
        System.out.println(String.format("StoragePool %s VolumeCount %s TotalCapacity %s AvailableCapacity %s",
                storagePoolResult.getName(), storagePoolResult.getVolumeCount(), storagePoolResult.getTotalCapacity(),
                storagePoolResult.getAvailableCapacity()));
    }

    @Test
    public void testQueryAllVolumes() {
        int thisMany = 3;
        for (int index = 0; index < thisMany; index++) {
            ScaleIOAddVolumeResult addVolumeResult =
                    scaleIOCLI.addVolume(TEST_PROTECTION_DOMAIN, TEST_POOL, String.format("%s-%d", TEST_VOLUME_NAME, index), "1");
            if (addVolumeResult.isSuccess())
                System.out.println(String.format("Volume %s (%s) size %s", addVolumeResult.getName(), addVolumeResult.getId(),
                        addVolumeResult.getActualSize()));
            else
                System.out.println(String.format("FAILED! %s", addVolumeResult.errorString()));
            assertTrue(String.format("Add volume failed %s\n", addVolumeResult.errorString()), addVolumeResult.isSuccess());
        }

        ScaleIOQueryAllVolumesResult queryAllVolumesResult =
                scaleIOCLI.queryAllVolumes();

        Collection<String> volumeIds = queryAllVolumesResult.getAllVolumeIds();
        assertTrue("Failed to query any volumes.", volumeIds.size() >= thisMany);
        int volumeCount = volumeIds.size();

        Collection<String> protectionDomainIds = queryAllVolumesResult.getProtectionDomainIds();
        assertTrue("Failed to get any ProtectionDomain ids", protectionDomainIds.size() > 0);

        for (String id : volumeIds) {
            ScaleIOAttributes attributes = queryAllVolumesResult.getScaleIOAttributesOfVolume(id);
            String name = attributes.get(ScaleIOQueryAllVolumesResult.VOLUME_NAME);
            String size = attributes.get(ScaleIOQueryAllVolumesResult.VOLUME_SIZE_BYTES);
            String poolName = attributes.get(ScaleIOQueryAllVolumesResult.VOLUME_POOL_NAME);
            System.out.println(String.format("Volume %s (%s) Size %s in pool %s", name, id, size, poolName));

            if (name.contains(TEST_VOLUME_NAME)) {
                ScaleIORemoveVolumeResult removeVolumeResult = scaleIOCLI.removeVolume(id);
                assertTrue(String.format("Remove volume failed for volume %s\n", removeVolumeResult.errorString()),
                        removeVolumeResult.isSuccess());
            }
        }

        queryAllVolumesResult = scaleIOCLI.queryAllVolumes();
        volumeIds = queryAllVolumesResult.getAllVolumeIds();
        int newVolumeCount = volumeIds.size();
        assertTrue("Not all the volumes were deleted", (volumeCount - newVolumeCount) == thisMany);
    }

    @Test
    public void testMapAndUnMapVolume() {
        List<String> addedVolumes = new ArrayList<>();
        int thisMany = 3;
        for (int index = 0; index < thisMany; index++) {
            ScaleIOAddVolumeResult addVolumeResult =
                    scaleIOCLI.addVolume(TEST_PROTECTION_DOMAIN, TEST_POOL, String.format("%s-%d", TEST_VOLUME_NAME, index), "1");
            if (addVolumeResult.isSuccess())
                System.out.println(String.format("Volume %s (%s) size %s", addVolumeResult.getName(), addVolumeResult.getId(),
                        addVolumeResult.getActualSize()));
            else
                System.out.println(String.format("FAILED! %s", addVolumeResult.errorString()));
            assertTrue(String.format("Add volume failed %s\n", addVolumeResult.errorString()), addVolumeResult.isSuccess());
            addedVolumes.add(addVolumeResult.getId());
        }

        ScaleIOQueryAllSDCResult queryAllSDCResult = scaleIOCLI.queryAllSDC();
        for (String sdcId : queryAllSDCResult.getSDCIds()) {
            for (String volumeId : addedVolumes) {
                ScaleIOMapVolumeToSDCResult mapVolumeToSDCResult =
                        scaleIOCLI.mapVolumeToSDC(volumeId, sdcId);
                assertTrue(String.format("Failed to map %s to %s. %s", volumeId, sdcId,
                        mapVolumeToSDCResult.getErrorString()),
                        mapVolumeToSDCResult.isSuccess());
                System.out.printf("Mapped volume %s to SDC %s.\n", volumeId, sdcId);
            }
        }

        for (String sdcId : queryAllSDCResult.getSDCIds()) {
            for (String volumeId : addedVolumes) {
                ScaleIOUnMapVolumeToSDCResult unMapVolumeToSDCResult =
                        scaleIOCLI.unMapVolumeToSDC(volumeId, sdcId);
                assertTrue(String.format("Failed to unmap %s to %s. %s", volumeId, sdcId,
                        unMapVolumeToSDCResult.getErrorString()),
                        unMapVolumeToSDCResult.isSuccess());
                System.out.printf("Unmapped volume %s to SDC %s.\n", volumeId, sdcId);
            }
        }

        ScaleIOQueryAllVolumesResult queryAllVolumesResult =
                scaleIOCLI.queryAllVolumes();

        Collection<String> volumeIds = queryAllVolumesResult.getAllVolumeIds();
        assertTrue("Failed to query any volumes.", volumeIds.size() >= thisMany);
        int volumeCount = volumeIds.size();

        for (String id : volumeIds) {
            ScaleIOAttributes attributes = queryAllVolumesResult.getScaleIOAttributesOfVolume(id);
            String name = attributes.get(ScaleIOQueryAllVolumesResult.VOLUME_NAME);
            String size = attributes.get(ScaleIOQueryAllVolumesResult.VOLUME_SIZE_BYTES);
            String poolName = attributes.get(ScaleIOQueryAllVolumesResult.VOLUME_POOL_NAME);
            System.out.println(String.format("Volume %s (%s) Size %s in pool %s", name, id, size, poolName));

            if (name.contains(TEST_VOLUME_NAME)) {
                ScaleIORemoveVolumeResult removeVolumeResult = scaleIOCLI.removeVolume(id);
                assertTrue(String.format("Remove volume failed for volume %s\n", removeVolumeResult.errorString()),
                        removeVolumeResult.isSuccess());
            }
        }

        queryAllVolumesResult = scaleIOCLI.queryAllVolumes();
        volumeIds = queryAllVolumesResult.getAllVolumeIds();
        int newVolumeCount = volumeIds.size();
        assertTrue("Not all the volumes were deleted", (volumeCount - newVolumeCount) == thisMany);
    }

    @Test
    public void testSCSIInitiator() {
        ScaleIOQueryAllSCSIInitiatorsResult initiatorsResult = scaleIOCLI.queryAllSCSIInitiators();
        dumpSCSIInitiator(initiatorsResult);

        if (!initiatorsResult.getAllInitiatorIds().iterator().hasNext()) {
            System.out.printf("There are no iSCSI initiators associated with the SIO instance to test with");
            return;
        }

        String pickOneInitiator = initiatorsResult.getAllInitiatorIds().iterator().next();

        ScaleIOAddVolumeResult addVolumeResult =
                scaleIOCLI.addVolume(TEST_PROTECTION_DOMAIN, TEST_POOL, String.format("%s-iSCSI", TEST_VOLUME_NAME), "1");

        ScaleIOMapVolumeToSCSIInitiatorResult mapResult =
                scaleIOCLI.mapVolumeToSCSIInitiator(addVolumeResult.getId(), pickOneInitiator);
        assertTrue(mapResult.isSuccess());
        initiatorsResult = scaleIOCLI.queryAllSCSIInitiators();
        dumpSCSIInitiator(initiatorsResult);

        assertTrue(initiatorsResult.isVolumeMappedToInitiator(addVolumeResult.getId(), pickOneInitiator));
        System.out.printf("Successfully mapped volume %s to %s\n", addVolumeResult.getId(), pickOneInitiator);

        mapResult =
                scaleIOCLI.mapVolumeToSCSIInitiator(addVolumeResult.getId(), pickOneInitiator);
        assertFalse(mapResult.isSuccess());

        ScaleIOUnMapVolumeFromSCSIInitiatorResult unmapResult =
                scaleIOCLI.unMapVolumeFromSCSIInitiator(addVolumeResult.getId(), pickOneInitiator);
        assertTrue(unmapResult.isSuccess());
        initiatorsResult = scaleIOCLI.queryAllSCSIInitiators();
        dumpSCSIInitiator(initiatorsResult);

        assertFalse(initiatorsResult.isVolumeMappedToInitiator(addVolumeResult.getId(), pickOneInitiator));
        System.out.printf("Successfully unmapped volume %s from %s\n", addVolumeResult.getId(), pickOneInitiator);

        unmapResult =
                scaleIOCLI.unMapVolumeFromSCSIInitiator(addVolumeResult.getId(), pickOneInitiator);
        assertFalse(unmapResult.isSuccess());

        scaleIOCLI.removeVolume(addVolumeResult.getId());
    }

    static void dumpSCSIInitiator(ScaleIOQueryAllSCSIInitiatorsResult result) {
        for (String iqn : result.getAllInitiatorIds()) {
            ScaleIOAttributes initiatorAttrs = result.getInitiator(iqn);
            System.out.print(String.format("Initiator: %s %s %s %s",
                    iqn,
                    initiatorAttrs.get(ScaleIOQueryAllSCSIInitiatorsResult.INITIATOR_NAME),
                    initiatorAttrs.get(ScaleIOQueryAllSCSIInitiatorsResult.INITIATOR_STATE),
                    initiatorAttrs.get(ScaleIOQueryAllSCSIInitiatorsResult.INITIATOR_ID)));
            if (result.hasMappedVolumes(iqn)) {
                System.out.println(" Has mapped volumes");
                for (Map.Entry<String, ScaleIOAttributes> entry : result.getMappedVolumes(iqn).entrySet()) {
                    ScaleIOAttributes volAttrs = entry.getValue();
                    System.out.println(String.format("Mapped volume: %s %s %s",
                            entry.getKey(),
                            volAttrs.get(ScaleIOQueryAllSCSIInitiatorsResult.VOLUME_NAME),
                            volAttrs.get(ScaleIOQueryAllSCSIInitiatorsResult.VOLUME_LUN)));
                }
            } else {
                System.out.println(" Has no mapped volumes");
            }
        }
    }

    static List<ScaleIOAddVolumeResult> createVolumes(int count) {
        List<ScaleIOAddVolumeResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ScaleIOAddVolumeResult addVolumeResult = scaleIOCLI.addVolume(TEST_PROTECTION_DOMAIN, TEST_POOL, generateUUID(), "1");
            if (addVolumeResult.isSuccess()) {
                System.out.println(String.format("Volume %s (%s) size %s", addVolumeResult.getName(), addVolumeResult.getId(),
                        addVolumeResult.getActualSize()));
            } else {
                System.out.println(String.format("FAILED! %s", addVolumeResult.errorString()));
            }
            assertTrue(String.format("Add volume failed %s\n", addVolumeResult.errorString()), addVolumeResult.isSuccess());
            results.add(addVolumeResult);
        }
        return results;
    }

    static void removeVolumes(List<ScaleIOAddVolumeResult> results) {
        for (ScaleIOAddVolumeResult result : results) {
            if (result.isSuccess()) {
                ScaleIORemoveVolumeResult removeVolumeResult = scaleIOCLI.removeVolume(result.getId());
                assertTrue(String.format("Remove volume failed for source volume %s\n", removeVolumeResult.errorString()),
                        removeVolumeResult.isSuccess());
            }
        }
    }

    static String generateUUID() {
        return String.format("%s%s", TEST_VOLUME_PREFIX, UUID.randomUUID().toString().substring(0, 10));
    }
}
