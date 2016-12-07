package com.emc.storageos.systemservices.impl.resource;

import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagedriver.StorageDriverList;
import com.emc.storageos.model.storagedriver.StorageDriverRestRep;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

public class StorageDriverServiceTest extends StorageDriverService {

    private DbClientImpl dbClient;
    private CoordinatorClientImpl coordinator;
    private CoordinatorClientExt coordinatorExt;
    private StorageDriverService service;

    @Before
    public void setUp() throws Exception {
        dbClient = mock(DbClientImpl.class);
        coordinator = mock(CoordinatorClientImpl.class);
        coordinatorExt = mock(CoordinatorClientExt.class);
        doReturn(coordinator).when(coordinatorExt).getCoordinatorClient();
        service = spy(new StorageDriverService());
        service.setDbClient(dbClient);
        service.setCoordinatorExt(coordinatorExt);
    }

    @Test
    public void testGetStorageDrivers() {
        // providertype1 is used
        doReturn(new HashSet<String>() {
            {
                add("systemtype1");
            }
        }).when(service).getUsedStorageProviderTypes();
        // no systemtype is used
        doReturn(new HashSet<String>()).when(service).getUsedStorageSystemTypes();

        StorageSystemType type1 = new StorageSystemType();
        type1.setStorageTypeDispName("systemtype1");
        type1.setStorageTypeName("systemtype1");
        type1.setIsNative(false);
        type1.setDriverStatus(StorageSystemType.STATUS.ACTIVE.toString());
        type1.setDriverName("driver1");

        StorageSystemType type2 = new StorageSystemType();
        type2.setStorageTypeDispName("providertype1");
        type2.setStorageTypeName("providertype1");
        type2.setIsNative(false);
        type2.setDriverStatus(StorageSystemType.STATUS.ACTIVE.toString());
        type2.setDriverName("driver1");

        StorageSystemType type3 = new StorageSystemType();
        type3.setStorageTypeDispName("systemtype2");
        type3.setStorageTypeName("systemtype2");
        type3.setIsNative(false);
        type3.setDriverStatus(StorageSystemType.STATUS.ACTIVE.toString());
        type3.setDriverName("driver2");

        List<StorageSystemType> types = new ArrayList<StorageSystemType>();
        types.add(type1);
        types.add(type2);
        types.add(type3);
        doReturn(types.iterator()).when(dbClient).queryIterativeObjects(eq(StorageSystemType.class),
                anyCollectionOf(URI.class));

        StorageDriverList driverList = service.getStorageDrivers();
        assertEquals(driverList.getDrivers().size(), 2);
        for (StorageDriverRestRep driver : driverList.getDrivers()) {
            if ("driver1".equals(driver.getDriverName())) {
                assertEquals(driver.getSupportedTypes().size(), 2);
                assertTrue(driver.getSupportedTypes().contains("systemtype1"));
                assertTrue(driver.getSupportedTypes().contains("providertype1"));
                assertEquals(driver.getDriverStatus(), "IN_USE");
            }
            if ("driver2".equals(driver.getDriverName())) {
                assertEquals(driver.getSupportedTypes().size(), 1);
                assertTrue(driver.getSupportedTypes().contains("systemtype2"));
                assertEquals(driver.getDriverStatus(), "READY");
            }
        }
    }
}
