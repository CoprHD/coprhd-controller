package com.emc.storageos.driver.univmax.sdkapi.discover;

import com.emc.storageos.driver.univmax.UniVmaxStorageDriver;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DiscoverStorageProviderTest {
    @Test
    public void discoverStorageProvider() throws Exception {
        UniVmaxStorageDriver driver = new UniVmaxStorageDriver();
        StorageProvider provider = new StorageProvider();
        provider.setUseSSL(true);
        provider.setProviderHost("10.247.97.150");
        provider.setPortNumber(RestClient.DEFAULT_PORT);
        provider.setUsername("smc");
        provider.setPassword("smc");
        System.out.println("port:" + provider.getPortNumber() + ", host:" + provider.getProviderHost() +
                ", user:" + provider.getUsername() + ", pass:" + provider.getPassword());
        List<StorageSystem> storageSystems = new ArrayList<>();

        DriverTask task = driver.discoverStorageProvider(provider, storageSystems);

        System.out.println("Task Status:\n\t" + task.getStatus().toString());
        System.out.println("Task Message:\n\t" + task.getMessage());
        System.out.println("Provider Version:\n\t" + provider.getProviderVersion());
        System.out.println("Supported Symmetrix Systems:");
        for (StorageSystem system : storageSystems) {
            System.out.println("\t" + system.getSerialNumber());
        }
    }

}