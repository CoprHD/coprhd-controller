package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.XtremIOStoragePortPortGroupMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class XtremIOStoragePortPortGroupMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(XtremIOStoragePortPortGroupMigrationTest.class);
    private static final String DEFAULT_NAME = "DEFAULT_NAME";

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.3", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new XtremIOStoragePortPortGroupMigration());
            }
        });

        DbsvcTestBase.setup();
        log.info("completed setup");
    }

    @Override
    protected String getSourceVersion() {
        return "2.3";
    }

    @Override
    protected String getTargetVersion() {
        return "2.4";
    }

    @Override
    protected void prepareData() throws Exception {
        URI systemURI = createStorageSystem();
        URI adapterURI = createStorageAdapter(systemURI);
        createStoragePort(systemURI, adapterURI);
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> storagePortURIs = _dbClient.queryByType(StoragePort.class, true);
        Iterator<StoragePort> storagePortsIter = _dbClient.queryIterativeObjects(StoragePort.class, storagePortURIs);
        while (storagePortsIter.hasNext()) {
            StoragePort storagePort = storagePortsIter.next();
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, storagePort.getStorageDevice());
            if (DiscoveredDataObject.Type.xtremio.name().equalsIgnoreCase(system.getSystemType())) {
                if (storagePort.getStorageHADomain() != null) {
                    StorageHADomain haDomain = _dbClient.queryObject(StorageHADomain.class, storagePort.getStorageHADomain());
                    Assert.assertEquals(haDomain.getAdapterName(), storagePort.getPortGroup());
                }
            }
        }
    }

    private URI createStorageSystem() {
        StorageSystem xioStorageSystem = new StorageSystem();
        xioStorageSystem.setId(URIUtil.createId(StorageSystem.class));
        xioStorageSystem.setSystemType(DiscoveredDataObject.Type.xtremio.name());
        xioStorageSystem.setNativeGuid("XTREMIO+APM00141017919");
        xioStorageSystem.setSerialNumber("APM00141017919");
        xioStorageSystem.setIpAddress("10.247.63.50");
        xioStorageSystem.setFirmwareVersion("3.0.2-14");
        _dbClient.createObject(xioStorageSystem);
        return xioStorageSystem.getId();
    }

    private URI createStorageAdapter(URI systemURI) {
        StorageHADomain adapter = new StorageHADomain();
        adapter.setId(URIUtil.createId(StorageHADomain.class));
        adapter.setStorageDeviceURI(systemURI);
        adapter.setNativeGuid("XTREMIO+APM00141017919+ADAPTER+X1-SC1");
        adapter.setAdapterName("X1-SC1");
        _dbClient.createObject(adapter);
        return adapter.getId();
    }

    private void createStoragePort(URI systemURI, URI adapterURI) {
        // port with DEFAULT PORT GROUP NAME
        StoragePort port = new StoragePort();
        port.setId(URIUtil.createId(StoragePort.class));
        port.setStorageDevice(systemURI);
        port.setStorageHADomain(adapterURI);
        port.setNativeGuid("XTREMIO+APM00141017919+PORT+51:4f:0c:56:d5:9f:e8:05");
        port.setPortGroup(DEFAULT_NAME);
        _dbClient.createObject(port);
    }
}
