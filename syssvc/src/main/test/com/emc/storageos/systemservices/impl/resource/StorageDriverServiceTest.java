/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.emc.storageos.coordinator.client.model.StorageDriverMetaData;
import com.emc.storageos.coordinator.client.model.StorageDriversInfo;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.model.storagedriver.StorageDriverList;
import com.emc.storageos.model.storagedriver.StorageDriverRestRep;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.sun.jersey.core.header.FormDataContentDisposition;

public class StorageDriverServiceTest extends StorageDriverService {

    private DbClientImpl dbClient;
    private CoordinatorClientImpl coordinator;
    private CoordinatorClientExt coordinatorExt;
    private StorageDriverService service;

    private StorageSystemType type1;
    private StorageSystemType type2;
    private StorageSystemType type3;

    @Before
    public void setUp() throws Exception {
        // mock dependent members of StorageDriverService instance and wire into
        // them
        dbClient = mock(DbClientImpl.class);
        doNothing().when(dbClient).createObject(any(StorageSystemType.class));
        doNothing().when(dbClient).updateObject(any(StorageSystemType.class));
        coordinator = mock(CoordinatorClientImpl.class);
        coordinatorExt = mock(CoordinatorClientExt.class);
        doReturn(coordinator).when(coordinatorExt).getCoordinatorClient();
        doNothing().when(coordinator).persistServiceConfiguration(any(Configuration.class));
        service = spy(new StorageDriverService());
        service.setDbClient(dbClient);
        service.setCoordinatorExt(coordinatorExt);

        // mock 3 storage system types in db
        type1 = new StorageSystemType();
        type1.setStorageTypeDispName("systemtype1");
        type1.setStorageTypeName("systemtype1");
        type1.setIsNative(false);
        type1.setDriverStatus(StorageSystemType.STATUS.ACTIVE.toString());
        type1.setDriverName("driver1");
        type1.setDriverFileName("driverFileName1");
        type1.setDriverVersion("1.2.3.4");

        type2 = new StorageSystemType();
        type2.setStorageTypeDispName("providertype1");
        type2.setStorageTypeName("providertype1");
        type2.setIsNative(false);
        type2.setDriverStatus(StorageSystemType.STATUS.ACTIVE.toString());
        type2.setDriverName("driver1");
        type2.setDriverFileName("driverFileName1");
        type2.setDriverVersion("1.2.3.4");

        type3 = new StorageSystemType();
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

        // mock that systemtype1 is in use
        Set<String> inUseTypes = new HashSet<String>();
        inUseTypes.add("systemtype1");
        doReturn(inUseTypes).when(service).getUsedStorageProviderTypes();

        // mock that no systemtype is used
        doReturn(new HashSet<String>()).when(service).getUsedStorageSystemTypes();
        // bypass pre-check for environment
        doNothing().when(service).precheckForEnv();

        // mock lock acquire and release
        InterProcessLock lock = mock(InterProcessLock.class);
        doNothing().when(lock).release();
        doReturn(lock).when(service).getStorageDriverOperationLock();

        // mock target list of installed drivers
        StorageDriversInfo drivers = new StorageDriversInfo();
        Set<String> installedDrivers = new HashSet<String>();
        installedDrivers.add("driverFileName1");
        installedDrivers.add("driverFileName2");
        drivers.setInstalledDrivers(installedDrivers);
        doReturn(drivers).when(coordinator).getTargetInfo(anyObject());

        // mock audit operation
        doNothing().when(service).auditOperation(any(OperationTypeEnum.class), anyString(), anyString(), anyObject());

        // mock file moving from tmp dir to data dir
        doNothing().when(service).moveDriverToDataDir(any(File.class));

        // mock updating target innfo
        doNothing().when(coordinator).setTargetInfo(anyObject());

        // mock progess updating
        doNothing().when(coordinatorExt).setNodeSessionScopeInfo(anyObject());

        // mock moving driver file to tmp dir
        doReturn(null).when(service).saveToTmpDir(anyString(), anyObject());
    }

    private StorageDriverMetaData createMetaData() {
        StorageDriverMetaData metaData = new StorageDriverMetaData();
        metaData = new StorageDriverMetaData();
        metaData.setDriverName("driverName");
        metaData.setDriverVersion("1.2.3.4");
        metaData.setStorageName("storage name");
        metaData.setStorageDisplayName("storage display name");
        metaData.setProviderName("provider name");
        metaData.setProviderDisplayName("provider display name");
        metaData.setMetaType("BLOCK");
        metaData.setEnableSsl(true);
        metaData.setSslPort(1234);
        metaData.setNonSslPort(4321);
        metaData.setDriverClassName("driverClassName");
        metaData.setDriverFileName("driverFileName");
        metaData.setSupportAutoTierPolicy(true);
        return metaData;
    }

    @Test
    public void testGetStorageDrivers() {
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

    @Test
    public void testGetSingleDriver() {
        StorageDriverRestRep driver = service.getSingleStorageDriver("driver1");
        assertEquals(driver.getDriverName(), "driver1");
        assertEquals(driver.getSupportedTypes().size(), 2);
        assertTrue(driver.getSupportedTypes().contains("systemtype1"));
        assertTrue(driver.getSupportedTypes().contains("providertype1"));
        assertEquals(driver.getDriverStatus(), "IN_USE");
    }

    @Test
    public void testInstall() throws Exception {
        FormDataContentDisposition details = mock(FormDataContentDisposition.class);
        doReturn(0L).when(details).getSize();

        // mock parsing meta data
        doReturn(createMetaData()).when(service).parseDriverMetaData(anyObject());
        try {
            service.install(null, details);
        } catch (Exception e) {
            fail("driver install failed");
        }
    }

    @Test
    public void testUninstall() {
        String driverName = "driver1";
        try {
            service.uninstall(driverName);
        } catch (Exception e) {
            fail("driver uninstall failed");
        }
    }

    @Test
    public void testUpgrade() {
        String driverName = "driver1";
        FormDataContentDisposition details = mock(FormDataContentDisposition.class);
        doReturn(0L).when(details).getSize();
        boolean force = false;
        StorageDriverMetaData metaData = createMetaData();
        metaData.setDriverName("driver1");
        metaData.setDriverVersion("1.2.3.5");
        // mock parsing meta data
        doReturn(metaData).when(service).parseDriverMetaData(anyObject());
        try {
            service.upgrade(driverName, null, details, force);
        } catch (Exception e) {
            fail("driver upgrade failed");
        }

    }
}
