package com.emc.storageos.driver.scaleio;

import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.DiscoveryDriver;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.junit.Test;
import org.junit.Assert;

import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by shujinwu on 11/17/15.
 */
public class ScaleIOStorageDriverTest {

    public ScaleIOStorageDriver driver = new ScaleIOStorageDriver();
    public DriverTaskImpl task;

    @Test
    public void testCreateVolumes() throws Exception {
        System.out.print("Tested!");
    }

    @Test
    public void testExpandVolume() throws Exception {

    }

    @Test
    public void testDeleteVolumes() throws Exception {

    }

    @Test
    public void testCreateVolumeSnapshot() throws Exception {

    }

    @Test
    public void testRestoreSnapshot() throws Exception {

    }

    @Test
    public void testDeleteVolumeSnapshot() throws Exception {

    }

    @Test
    public void testCreateVolumeClone() throws Exception {

    }

    @Test
    public void testDetachVolumeClone() throws Exception {

    }

    @Test
    public void testRestoreFromClone() throws Exception {

    }

    @Test
    public void testDeleteVolumeClone() throws Exception {

    }

    @Test
    public void testCreateVolumeMirror() throws Exception {

    }

    @Test
    public void testDeleteVolumeMirror() throws Exception {

    }

    @Test
    public void testSplitVolumeMirror() throws Exception {

    }

    @Test
    public void testResumeVolumeMirror() throws Exception {

    }

    @Test
    public void testRestoreVolumeMirror() throws Exception {

    }

    @Test
    public void testGetITL() throws Exception {

    }

    @Test
    public void testExportVolumesToInitiators() throws Exception {

    }

    @Test
    public void testUnexportVolumesFromInitiators() throws Exception {

    }

    @Test
    public void testCreateConsistencyGroup() throws Exception {

    }

    @Test
    public void testCreateConsistencyGroupSnapshot() throws Exception {

    }

    @Test
    public void testDeleteConsistencyGroupSnapshot() throws Exception {

    }

    @Test
    public void testCreateConsistencyGroupClone() throws Exception {

    }

    @Test
    public void testDeleteConsistencyGroupClone() throws Exception {

    }

    @Test
    public void testGetRegistrationData() throws Exception {

    }

    @Test
    public void testDiscoverStorageSystem() throws Exception {
        List<StorageSystem> storageSystems = new ArrayList<>();
        StorageSystem validStorageSystem = new StorageSystem();
        StorageSystem invalidStorageSystem = new StorageSystem();

        validStorageSystem.setSystemName("TestValidSystem");
        validStorageSystem.setSystemType("scaleio");
        //validStorageSystem.setPortNumber();
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.97");

        // Valid list of storage systems
        storageSystems.add(validStorageSystem);

        task = (DriverTaskImpl) driver.discoverStorageSystem(storageSystems);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        //invalidStorageSystem.setPortNumber();
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


        validStorageSystem.setSystemName("TestValidSystem");
        validStorageSystem.setSystemType("scaleio");
        //validStorageSystem.setPortNumber();
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.97");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        //invalidStorageSystem.setPortNumber();
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        List<StoragePool> storagePools = new ArrayList<>();
        StoragePool storagePool = new StoragePool();
        storagePools.add(storagePool);

        task = (DriverTaskImpl) driver.discoverStoragePools(validStorageSystem, storagePools);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        task = (DriverTaskImpl) driver.discoverStoragePools(invalidStorageSystem, storagePools);
        System.out.println(task);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");
    }

    @Test
    public void testDiscoverStoragePorts() throws Exception {
        StorageSystem validStorageSystem = new StorageSystem();
        StorageSystem invalidStorageSystem = new StorageSystem();
        List<StoragePort> storagePorts = new ArrayList<>();

        validStorageSystem.setSystemName("TestValidSystem");
        validStorageSystem.setSystemType("scaleio");
        //validStorageSystem.setPortNumber();
        validStorageSystem.setUsername("admin");
        validStorageSystem.setPassword("Scaleio123");
        validStorageSystem.setIpAddress("10.193.17.97");

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        //invalidStorageSystem.setPortNumber();
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        // Valid system, empty list
        task = (DriverTaskImpl) driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Valid system, invalid list
        task = (DriverTaskImpl) driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertNotEquals(task.getStatus().toString(), "READY");

        // Valid system, valid list
        task = (DriverTaskImpl) driver.discoverStoragePorts(validStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertEquals(task.getStatus().toString(), "READY");

        // Invalid system
        task = (DriverTaskImpl) driver.discoverStoragePorts(invalidStorageSystem, storagePorts);

        Assert.assertNotNull(task);
        Assert.assertNotEquals(task.getStatus().toString(), "READY");

    }

    @Test
    public void testGetStorageVolumes() throws Exception {
        // We are not implementing this for the current release

    }

    @Test
    public void testGetSystemTypes() throws Exception {

    }

    @Test
    public void testGetTask() throws Exception {

    }

    @Test
    public void testGetStorageObject() throws Exception {

    }
}