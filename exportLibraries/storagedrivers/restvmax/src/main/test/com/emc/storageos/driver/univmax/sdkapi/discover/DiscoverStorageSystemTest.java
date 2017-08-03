package com.emc.storageos.driver.univmax.sdkapi.discover;

import com.emc.storageos.driver.univmax.UniVmaxStorageDriver;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DiscoverStorageSystemTest {
    @Test
    public void discoverStorageSystem() throws Exception {
        UniVmaxStorageDriver driver = new UniVmaxStorageDriver();
        StorageSystem storageSystem = new StorageSystem();
        storageSystem.setIpAddress("10.247.97.150");
        storageSystem.setPortNumber(RestClient.DEFAULT_PORT);
        storageSystem.setUsername("smc");
        storageSystem.setPassword("smc");
        storageSystem.setNativeId("000196800794");

        RestClient client = new RestClient(true, storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword());

        driver.getDriverDataUtil().addRestClient(storageSystem.getNativeId(), client);
        DriverTask task = driver.discoverStorageSystem(storageSystem);

        System.out.println("Task Status:\n\t" + task.getStatus().toString());
        System.out.println("Task Message:\n\t" + task.getMessage());
        System.out.println("System Model:\n\t" + storageSystem.getModel());
        System.out.println("System Serial #:\n\t" + storageSystem.getSerialNumber());
    }

}