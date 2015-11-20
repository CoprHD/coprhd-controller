package com.emc.storageos.driver.scaleio;

import com.emc.storageos.storagedriver.DiscoveryDriver;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by shujinwu on 11/17/15.
 */
public class ScaleIOStorageDriverTest {

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
        //DiscoveryDriver discoveryDriver = new DiscoveryDriver();
        //DriverTask task = new DriverTask();
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

        //discoveryDriver.discoverStorageSystem(storageSystems);
        //System.out.print(task);

        invalidStorageSystem.setSystemName("TestInvalidSystem");
        invalidStorageSystem.setSystemType("scaleio");
        //invalidStorageSystem.setPortNumber();
        invalidStorageSystem.setUsername("username");
        invalidStorageSystem.setPassword("password");
        invalidStorageSystem.setIpAddress("10.193.17.99");

        // Partially valid list of storage systems
        storageSystems.add(invalidStorageSystem);

        //discoveryDriver.discoverStorageSystem(storageSystems);
        //System.out.print(task);

        // Invalid list of storage systems
        storageSystems.remove(0);

        //discoveryDriver.discoverStorageSystem(storageSystems);
        // System.out.print(task);

        // Empty list of storage systems
        storageSystems.remove(0);

        //discoveryDriver.discoverStorageSystem(storageSystems);
        // System.out.print(task);

    }

    @Test
    public void testDiscoverStoragePools() throws Exception {
        //DiscoveryDriver discoveryDriver = new DiscoveryDriver();
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

        //discoveryDriver.discoverStoragePools(validStorageSystem, storagePools);
        //discoveryDriver.discoverStoragePools(invalidStorageSystem, storagePools);

    }

    @Test
    public void testDiscoverStoragePorts() throws Exception {
        //DiscoveryDriver discoveryDriver = new DiscoveryDriver();
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


        List<StoragePort> storagePorts = new ArrayList<>();
        StoragePort storagePort = new StoragePort();
        storagePorts.add(storagePort);

        //discoveryDriver.discoverStoragePools(validStorageSystem, storagePools);
        //discoveryDriver.discoverStoragePools(invalidStorageSystem, storagePools);
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