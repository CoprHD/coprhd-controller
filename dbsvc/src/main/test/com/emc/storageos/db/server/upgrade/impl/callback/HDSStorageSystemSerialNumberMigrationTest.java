/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2015 EMC Corporation
 *
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.HDSStorageSystemSerialNumberMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Test upgrade of HDS storagesystem serialNumber.
 * In 2.0 HDS storagesystem's serialNumber was populated as "ARRAY.R700.94677"
 * afterwards it has been changed to "94677".
 * 
 * This tests verify whether we are properly updating the serial number of the HDS systems or not.
 */
public class HDSStorageSystemSerialNumberMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(HDSStorageSystemSerialNumberMigrationTest.class);

    private static volatile StorageSystem hdsStorageSystem;
    private static volatile StorageSystem otherStorageSystem;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.0", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;

            {
                add(new HDSStorageSystemSerialNumberMigration());
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
        return "2.2";
    }

    @Override
    protected void prepareData() throws Exception {
        createStorageSystems();
    }

    @Override
    protected void verifyResults() throws Exception {
        StorageSystem checkHdsStorageSystem = _dbClient.queryObject(StorageSystem.class, hdsStorageSystem.getId());
        assertNotNull(checkHdsStorageSystem);
        assertStorageSystemSerialNumberValue("serailNumber", "94677", checkHdsStorageSystem.getSerialNumber());

        StorageSystem checkOtherStorageSystem = _dbClient.queryObject(StorageSystem.class, otherStorageSystem.getId());
        assertNotNull(checkOtherStorageSystem);
        assertStorageSystemSerialNumberValue("serailNumber", "0001230002300", checkHdsStorageSystem.getSerialNumber());

    }

    private void assertStorageSystemSerialNumberValue(String name, Object expected, Object actual) {
        assertTrue(String.format("StorageSystem parameter %s should be %s, but is %s", name,
                expected.toString(), actual.toString()),
                actual.toString().equals(expected.toString()));
    }

    private void createStorageSystems() {
        hdsStorageSystem = new StorageSystem();
        hdsStorageSystem.setId(URIUtil.createId(StorageSystem.class));
        hdsStorageSystem.setSystemType(DiscoveredDataObject.Type.hds.name());
        hdsStorageSystem.setNativeGuid("HDS+ARRAY.R700.94677");
        hdsStorageSystem.setSerialNumber("ARRAY.R700.94677");
        hdsStorageSystem.setIpAddress("lglw6089.lss.emc.com");
        hdsStorageSystem.setModel("VSP");
        hdsStorageSystem.setFirmwareVersion("1.20.1.22");
        _dbClient.createObject(hdsStorageSystem);

        otherStorageSystem = new StorageSystem();
        otherStorageSystem.setId(URIUtil.createId(StorageSystem.class));
        otherStorageSystem.setSystemType(DiscoveredDataObject.Type.vmax.name());
        otherStorageSystem.setNativeGuid("SYMMETRIX+0001230002300");
        otherStorageSystem.setSerialNumber("0001230002300");
        otherStorageSystem.setIpAddress("10.1.123.124");
        otherStorageSystem.setModel("VMAX40K");
        otherStorageSystem.setFirmwareVersion("5977.22");
        _dbClient.createObject(otherStorageSystem);
    }

}
