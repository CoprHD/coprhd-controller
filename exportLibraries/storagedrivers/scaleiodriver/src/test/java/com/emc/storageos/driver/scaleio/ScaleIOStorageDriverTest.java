/*
 * Copyright 2016 Oregon State University
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
package com.emc.storageos.driver.scaleio;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;
import com.emc.storageos.storagedriver.model.*;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

@RunWith(value = SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/scaleio-driver-prov.xml" })
public class ScaleIOStorageDriverTest {

    String SYS_NATIVE_ID_A = "08bee35c00000000";
    String SYS_NATIVE_ID_B = "08af5d6100000000";
    String IP_ADDRESS_A = "10.193.17.97";
    String IP_ADDRESS_B = "10.193.17.88";

    int PORT_NUMBER = 443;
    String USER_NAME = "admin";
    String PASSWORD = "Scaleio123";
    String VOLUME_ID_1A = "08bee35d00000001";
    String VOLUME_ID_2A = "08bee35e00000002";
    String SNAPSHOT_OF_1A = "08bee35f00000003";
    String VOLUME_ID_1B = "83f1771b00000000";
    String VOLUME_ID_2B = "83f1771000000001";
    String INVALID_VOLUME_ID_1 = "83f177070000000";

    private ScaleIOStorageDriver driver;
    // @Autowired
    // private ScaleIORestHandleFactory scaleIORestHandleFactory;
    private DriverTask task;

    @Before
    public void setUp() throws Exception {
        Registry registry = new InMemoryRegistryImpl();
        driver = new ScaleIOStorageDriver();
        // driver.setHandleFactory(handleFactory);
        // driver.setScaleIORestHandleFactory(scaleIORestHandleFactory);
        driver.setDriverRegistry(registry);
        // testDiscoverStorageSystem();
    }

    @Test
    public void testCreateVolumes() throws Exception {
        List<StorageVolume> storageVolumes = new ArrayList<>();
        StorageCapabilities capabilities = null;

        // Create one or more volumes of varying sizes
        Random random = new Random();
        int numVolumes = random.nextInt(10) + 1;

        for (int i = 0; i < numVolumes; i++) {
            Long requestedCapacity = Long.valueOf(random.nextInt(100) + 1);

            StorageVolume volume = new StorageVolume();
            volume.setStorageSystemId("a817f58300000000");
            volume.setStoragePoolId("84c44afd00000000");
            volume.setRequestedCapacity(requestedCapacity);

            storageVolumes.add(volume);
        }

        task = driver.createVolumes(storageVolumes, capabilities);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        storageVolumes.clear();

        // Create volume with invalid size
        StorageVolume volume = new StorageVolume();

        volume.setStorageSystemId("a817f58300000000");
        volume.setStoragePoolId("84c44afd00000000");
        volume.setRequestedCapacity(Long.valueOf(-2));

        storageVolumes.add(volume);

        task = driver.createVolumes(storageVolumes, capabilities);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "FAILED");

        /* Create volume without connectivity? */
    }

    @Test
    public void testExpandVolume() throws Exception {
        StorageVolume volume = new StorageVolume();
        List<StorageVolume> storageVolumes = new ArrayList<>();
        StorageCapabilities capabilities = null;

        volume.setStorageSystemId("a817f58300000000");
        volume.setStoragePoolId("84c44afd00000000");
        volume.setRequestedCapacity(Long.valueOf(10));
        volume.setThinVolumePreAllocationSize(Long.valueOf(10));

        driver.createVolumes(storageVolumes, capabilities);

        Long capacity = volume.getProvisionedCapacity();
        System.out.println(capacity);
        capacity += 100;

        // Expand storage volume
        task = driver.expandVolume(volume, capacity);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Expand storage volume to invalid size
        task = driver.expandVolume(volume, -100);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "FAILED");

        // Expand storage volume that does not already exist in the storage system
        StorageVolume newVolume = new StorageVolume();

        task = driver.expandVolume(newVolume, capacity);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "FAILED");

        /* Expand storage volume w/o connectivity? */

    }

    @Test
    public void testDeleteVolumes() throws Exception {
        List<StorageVolume> storageVolumes = new ArrayList<>();
        StorageCapabilities capabilities = null;

        StorageVolume volume1 = new StorageVolume();
        StorageVolume volume2 = new StorageVolume();

        volume1.setStorageSystemId("a817f58300000000");
        volume1.setStoragePoolId("84c44afd00000000");
        volume1.setRequestedCapacity(Long.valueOf(20));
        volume1.setThinVolumePreAllocationSize(Long.valueOf(20));
        storageVolumes.add(volume1);

        volume2.setStorageSystemId("a817f58300000000");
        volume2.setStoragePoolId("84c44afd00000000");
        volume2.setRequestedCapacity(Long.valueOf(10));
        volume2.setThinVolumePreAllocationSize(Long.valueOf(10));
        storageVolumes.add(volume2);

        driver.createVolumes(storageVolumes, capabilities);

        // Delete storage volumes
        task = driver.deleteVolumes(storageVolumes);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Delete storage volume that does not already exist in the storage system
        StorageVolume newVolume = new StorageVolume();
        storageVolumes.clear();
        storageVolumes.add(newVolume);

        task = driver.deleteVolumes(storageVolumes);
        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "FAILED");

        /* Delete a storage volume w/o connectivity? */
    }

    @Test
    public void testDiscoverStorageSystem() throws Exception {
        List<StorageSystem> storageSystems = new ArrayList<>();
        StorageSystem validStorageSystem = new StorageSystem();
        StorageSystem invalidStorageSystem = new StorageSystem();

        validStorageSystem.setSystemName("pdomain");
        validStorageSystem.setSystemType("scaleio");
        validStorageSystem.setPortNumber(443);
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.88");

        // Valid list of storage systems
        storageSystems.add(validStorageSystem);

        task = driver.discoverStorageSystem(storageSystems);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        invalidStorageSystem.setPortNumber(443);
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        // Partially valid list of storage systems
        storageSystems.add(invalidStorageSystem);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Invalid list of storage systems
        storageSystems.remove(0);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Empty list of storage systems
        storageSystems.remove(0);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

    }

    @Test
    public void testDiscoverStoragePools() throws Exception {
        StorageSystem validStorageSystem = new StorageSystem();
        StorageSystem invalidStorageSystem = new StorageSystem();

        validStorageSystem.setSystemType("scaleio");
        validStorageSystem.setPortNumber(443);
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.97");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        invalidStorageSystem.setPortNumber(443);
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        List<StoragePool> storagePools = new ArrayList<>();
        StoragePool storagePool = new StoragePool();
        storagePools.add(storagePool);
        driver.setConnInfoToRegistry(validStorageSystem.getNativeId(), validStorageSystem.getIpAddress(),
                validStorageSystem.getPortNumber(), validStorageSystem.getUsername(), validStorageSystem.getPassword());
        task = driver.discoverStoragePools(validStorageSystem, storagePools);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        task = driver.discoverStoragePools(invalidStorageSystem, storagePools);
        System.out.println(task);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "ABORTED");
    }

    @Test
    public void testDiscoverStoragePorts() throws Exception {
        StorageSystem validStorageSystem = new StorageSystem();
        StorageSystem invalidStorageSystem = new StorageSystem();
        List<StoragePort> storagePorts = new ArrayList<>();

        validStorageSystem.setNativeId("08af5d6100000000");
        validStorageSystem.setSystemType("scaleio");
        validStorageSystem.setPortNumber(443);
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.88");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        // invalidStorageSystem.setPortNumber();
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        // Valid system, empty list
        task = driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Valid system, invalid list
        task = driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Valid system, valid list
        task = driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Invalid system
        task = driver.discoverStoragePorts(invalidStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "ABORTED");

    }

    @Test
    public void testGetConnInfoFromRegistry() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        Assert.assertEquals(IP_ADDRESS_A, driver.getConnInfoFromRegistry(SYS_NATIVE_ID_A, ScaleIOConstants.IP_ADDRESS));
        Assert.assertEquals(Integer.toString(PORT_NUMBER), driver.getConnInfoFromRegistry(SYS_NATIVE_ID_A, ScaleIOConstants.PORT_NUMBER));
        Assert.assertEquals(USER_NAME, driver.getConnInfoFromRegistry(SYS_NATIVE_ID_A, ScaleIOConstants.USER_NAME));
        Assert.assertEquals(PASSWORD, driver.getConnInfoFromRegistry(SYS_NATIVE_ID_A, ScaleIOConstants.PASSWORD));
    }

    @Test
    public void testCreateVolumeSnapshot() throws Exception {

        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        // test with null input parameters
        List<VolumeSnapshot> snapshots = null;
        DriverTask task = driver.createVolumeSnapshot(snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // create snapshots for volumes from same storage system
        snapshots = this.createSnapListSameSys(false);
        task = driver.createVolumeSnapshot(snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());
        this.checkResultSnapList(snapshots);

        snapshots = this.createSnapListSameSys(true);
        task = driver.createVolumeSnapshot(snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("PARTIALLY_FAILED", task.getStatus().toString());
        this.checkResultSnapList(snapshots);

        // create snapshots for volumes from different storage systems
        snapshots = this.createSnapListDiffSys(false);
        task = driver.createVolumeSnapshot(snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());
        this.checkResultSnapList(snapshots);

        snapshots = this.createSnapListDiffSys(true);
        task = driver.createVolumeSnapshot(snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("PARTIALLY_FAILED", task.getStatus().toString());
        this.checkResultSnapList(snapshots);
    }

    @Test
    public void testDeleteVolumeSnapshot() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        // test with null input parameters
        List<VolumeSnapshot> snapshots = null;
        DriverTask task = driver.deleteVolumeSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // list of valid snapshot
        snapshots = this.createSnapListDiffSys(false);
        driver.createVolumeSnapshot(snapshots, null);
        task = driver.deleteVolumeSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());

        // some of the snapshot are not existed
        snapshots = this.createSnapListDiffSys(true);
        driver.createVolumeSnapshot(snapshots, null);
        task = driver.deleteVolumeSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("PARTIALLY_FAILED", task.getStatus().toString());
    }

    @Test
    public void testCreateConsistencyGroupSnapshot() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        // test with null input parameters
        List<VolumeSnapshot> snapshots = null;
        VolumeConsistencyGroup cg = null;
        DriverTask task = driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // volumes from same storage system
        snapshots = this.createSnapListSameSys(false);
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());
        Assert.assertNotNull(cg.getNativeId());
        for (VolumeSnapshot snapshot : snapshots) {
            Assert.assertNotNull(snapshot.getNativeId());
            Assert.assertNotNull(snapshot.getConsistencyGroup());
        }

        // same storage system, some volumes are not existed
        snapshots = this.createSnapListSameSys(true);
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());
        Assert.assertNull(cg.getNativeId());

        // volumes from different storage system
        snapshots = this.createSnapListDiffSys(false);
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());
        Assert.assertNull(cg.getNativeId());

    }

    @Test
    public void testDeleteConsistencyGroupSnapshot() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);

        // null
        List<VolumeSnapshot> snapshots = null;
        DriverTask task = driver.deleteConsistencyGroupSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // snapshots are in same consistency group (w/o un-existed snapshot)
        snapshots = this.createSnapListSameCG(false);
        task = driver.deleteConsistencyGroupSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());

        snapshots = this.createSnapListSameCG(true);
        task = driver.deleteConsistencyGroupSnapshot(snapshots); // snapshots not existed
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // snapshots in different consistency group
        snapshots = this.createSnapListDiffCG(false);
        task = driver.deleteConsistencyGroupSnapshot(snapshots);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

    }

    /**
     * Initialize one snapshot
     *
     * @param nativeId
     * @param parentId
     * @param storageSystemId
     * @return
     */
    private VolumeSnapshot initializeSnapshot(String nativeId, String parentId, String storageSystemId) {
        VolumeSnapshot snapshot = new VolumeSnapshot();
        snapshot.setStorageSystemId(storageSystemId);
        snapshot.setParentId(parentId);
        snapshot.setNativeId(nativeId);
        return snapshot;

    }

    /**
     * Initialized a list of snapshots whose parent volumes are from same storage system
     *
     * @param withInvalid
     * @return
     */
    private List<VolumeSnapshot> createSnapListSameSys(boolean withInvalid) {
        List<VolumeSnapshot> snapshots = new LinkedList<>();
        snapshots.add(initializeSnapshot(null, VOLUME_ID_1A, SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, SNAPSHOT_OF_1A, SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, VOLUME_ID_2A, SYS_NATIVE_ID_A));
        if (withInvalid) {
            snapshots.add(initializeSnapshot(null, INVALID_VOLUME_ID_1, SYS_NATIVE_ID_A));
        }
        return snapshots;
    }

    /**
     * Initialized a list of snapshots whose parent volumes are from different storage systems
     *
     * @param withInvalid
     * @return
     */
    private List<VolumeSnapshot> createSnapListDiffSys(boolean withInvalid) {
        List<VolumeSnapshot> snapshots = new LinkedList<>();
        snapshots.add(initializeSnapshot(null, VOLUME_ID_1A, SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, VOLUME_ID_2A, SYS_NATIVE_ID_A));
        snapshots.add(initializeSnapshot(null, VOLUME_ID_1B, SYS_NATIVE_ID_B));
        snapshots.add(initializeSnapshot(null, VOLUME_ID_2B, SYS_NATIVE_ID_B));
        if (withInvalid) {
            snapshots.add(initializeSnapshot(null, INVALID_VOLUME_ID_1, SYS_NATIVE_ID_B));
        }
        return snapshots;
    }

    /**
     * Validate if each snapshot is assigned with a nativeId in the resulting snapshot list
     *
     * @param snapshots
     */
    private void checkResultSnapList(List<VolumeSnapshot> snapshots) {
        for (VolumeSnapshot snapshot : snapshots) {
            if (!snapshot.getParentId().equalsIgnoreCase(INVALID_VOLUME_ID_1)) {
                Assert.assertNotNull(snapshot.getNativeId());
            } else {
                Assert.assertNull(snapshot.getNativeId());
            }
        }
    }

    /**
     * initialize a list of snapshots that in the same consistency group
     *
     * @param withInvalid
     * @return
     */
    private List<VolumeSnapshot> createSnapListSameCG(boolean withInvalid) {
        List<VolumeSnapshot> snapshots = this.createSnapListSameSys(withInvalid);
        VolumeConsistencyGroup cg = new VolumeConsistencyGroup();
        driver.createConsistencyGroupSnapshot(cg, snapshots, null);
        return snapshots;
    }

    /**
     * initialize a list of snapshots that in the different consistency group
     *
     * @param withInvalid
     * @return
     */
    private List<VolumeSnapshot> createSnapListDiffCG(boolean withInvalid) {
        List<VolumeSnapshot> snapshots = this.createSnapListSameCG(false);

        // create another group of snapshots
        List<VolumeSnapshot> snapshotsB = new LinkedList<>();
        snapshotsB.add(initializeSnapshot(null, VOLUME_ID_1B, SYS_NATIVE_ID_B));
        snapshotsB.add(initializeSnapshot(null, VOLUME_ID_2B, SYS_NATIVE_ID_B));
        VolumeConsistencyGroup cgB = new VolumeConsistencyGroup();
        driver.createConsistencyGroupSnapshot(cgB, snapshotsB, null);

        snapshots.addAll(snapshotsB);
        return snapshots;
    }
    @Test
    public void testCreateVolumeClone() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        // test with null input parameters
        List<VolumeClone> clone = null;
        DriverTask task = driver.createVolumeClone(clone, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        //Create clone for volumes from same storage system
        clone = this.createCloneListSameSys(false);
        task = driver.createVolumeClone(clone, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());
        this.checkResultCloneList(clone);

        //Create clone for volumes from same storage system
        clone = this.createCloneListSameSys(true);
        task = driver.createVolumeClone(clone, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("PARTIALLY_FAILED", task.getStatus().toString());
        this.checkResultCloneList(clone);

        // create clone for volumes from different storage systems
        clone= this.createCloneListDiffSys(false);
        task = driver.createVolumeClone(clone, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());
        this.checkResultCloneList(clone);

        //Create clone for volumes from different storage system
        clone = this.createCloneListDiffSys(true);
        task = driver.createVolumeClone(clone, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("PARTIALLY_FAILED", task.getStatus().toString());
        this.checkResultCloneList(clone);

    }


    @Test
    public void testDetachVolumeClone() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        List<VolumeClone> clone = null;
        DriverTask task = driver.detachVolumeClone(clone);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // Detach clone for volumes from same storage system
        clone = this.createCloneListSameSys(false);
        task = driver.detachVolumeClone(clone);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());

    }

    @Test
    public void testCreateConsistencyGroupClone() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        // test with null input parameters
        List<VolumeClone> clones = null;
        VolumeConsistencyGroup cg = null;
        DriverTask task = driver.createConsistencyGroupClone(cg, clones, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        // volumes from same storage system
        clones = this.createCloneListSameCG(false);
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupClone(cg, clones, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());
        Assert.assertNotNull(cg.getNativeId());
        for (VolumeClone clone : clones) {
            Assert.assertNotNull(clone.getNativeId());
            Assert.assertNotNull(clone.getConsistencyGroup());
        }

        // same storage system, some volumes are not existed
        clones = this.createCloneListSameCG(true);
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupClone(cg, clones, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());
        Assert.assertNull(cg.getNativeId());

        // volumes from different storage system
        clones = this.createCloneListDiffCG(false);
        cg = new VolumeConsistencyGroup();
        task = driver.createConsistencyGroupClone(cg, clones, null);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());
        Assert.assertNull(cg.getNativeId());

    }

    @Test
    public void testDetachConsistencyGroupClone() throws Exception {
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_A, IP_ADDRESS_A, PORT_NUMBER, USER_NAME, PASSWORD);
        driver.setConnInfoToRegistry(SYS_NATIVE_ID_B, IP_ADDRESS_B, PORT_NUMBER, USER_NAME, PASSWORD);
        List<VolumeClone> clones = null;
        VolumeConsistencyGroup cg = null;
        DriverTask task = driver.detachConsistencyGroupClone(clones);
        Assert.assertNotNull(task);
        Assert.assertEquals("FAILED", task.getStatus().toString());

        //Volume from same storage system
        clones = this.createCloneListSameCG(false);
        task = driver.detachConsistencyGroupClone(clones);
        Assert.assertNotNull(task);
        Assert.assertEquals("READY", task.getStatus().toString());

    }

    /**
     * Initialize one clone
     *
     * @param nativeId
     * @param parentId
     * @param storageSystemId
     * @return
     */
    private VolumeClone initializeClone(String nativeId, String parentId, String storageSystemId) {
        VolumeClone clone = new VolumeClone();
        clone.setStorageSystemId(storageSystemId);
        clone.setParentId(parentId);
        clone.setNativeId(nativeId);
        return clone;

    }

    /**
     * Initialized a list of clone whose parent volumes are from same storage system
     *
     * @param withInvalid
     * @return
     */
    private List<VolumeClone> createCloneListSameSys(boolean withInvalid) {
        List<VolumeClone> clones = new LinkedList<>();
        clones.add(initializeClone(null, VOLUME_ID_1A, SYS_NATIVE_ID_A));
        clones.add(initializeClone(null, SNAPSHOT_OF_1A, SYS_NATIVE_ID_A));
        clones.add(initializeClone(null, VOLUME_ID_2A, SYS_NATIVE_ID_A));
        if (withInvalid) {
            clones.add(initializeClone(null, INVALID_VOLUME_ID_1, SYS_NATIVE_ID_A));
        }
        return clones;
    }

    /**
     * Initialized a list of clones whose parent volumes are from different storage systems
     *
     * @param withInvalid
     * @return
     */
    private List<VolumeClone> createCloneListDiffSys(boolean withInvalid) {
        List<VolumeClone> clones = new LinkedList<>();
        clones.add(initializeClone(null, VOLUME_ID_1A, SYS_NATIVE_ID_A));
        clones.add(initializeClone(null, VOLUME_ID_2A, SYS_NATIVE_ID_A));
        clones.add(initializeClone(null, VOLUME_ID_1B, SYS_NATIVE_ID_B));
        clones.add(initializeClone(null, VOLUME_ID_2B, SYS_NATIVE_ID_B));
        if (withInvalid) {
            clones.add(initializeClone(null, INVALID_VOLUME_ID_1, SYS_NATIVE_ID_B));
        }
        return clones;
    }

    /**
     * Validate if each snapshot is assigned with a nativeId in the resulting snapshot list
     *
     * @param
     */
    private void checkResultCloneList(List<VolumeClone> clones) {
        for (VolumeClone clone : clones) {
            if (!clone.getParentId().equalsIgnoreCase(INVALID_VOLUME_ID_1)) {
                Assert.assertNotNull(clone.getNativeId());
            } else {
                Assert.assertNull(clone.getNativeId());
            }
        }
    }

    /**
     * initialize a list of clones that in the same consistency group
     *
     * @param withInvalid
     * @return
     */
    private List<VolumeClone> createCloneListSameCG(boolean withInvalid) {
        List<VolumeClone> clones = this.createCloneListSameSys(withInvalid);
        VolumeConsistencyGroup cg = new VolumeConsistencyGroup();
        driver.createConsistencyGroupClone(cg, clones, null);
        return clones;
    }

    /**
     * initialize a list of snapshots that in the different consistency group
     *
     * @param withInvalid
     * @return
     */
    private List<VolumeClone> createCloneListDiffCG(boolean withInvalid) {
        List<VolumeClone> clones = this.createCloneListSameSys(false);

        // create another group of clones
        List<VolumeClone> clonesB = new LinkedList<>();
        clonesB.add(initializeClone(null, VOLUME_ID_1B, SYS_NATIVE_ID_B));
        clonesB.add(initializeClone(null, VOLUME_ID_2B, SYS_NATIVE_ID_B));
        VolumeConsistencyGroup cgB = new VolumeConsistencyGroup();
        driver.createConsistencyGroupClone(cgB, clonesB, null);

        clones.addAll(clonesB);
        return clones;
    }
}
