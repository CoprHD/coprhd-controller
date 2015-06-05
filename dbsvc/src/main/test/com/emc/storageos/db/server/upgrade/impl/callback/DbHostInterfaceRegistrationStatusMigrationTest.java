/**
 *  Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.HostInterfaceRegistrationStatusMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper population of the new HostInterface.registrationStatus field
 * 
 * Here's the basic execution flow for the test case:
 * - setup() runs, bringing up a "pre-migration" version
 *   of the database, using the DbSchemaScannerInterceptor
 *   you supply to hide your new field or column family
 *   when generating the "before" schema. 
 * - Your implementation of prepareData() is called, allowing
 *   you to use the internal _dbClient reference to create any 
 *   needed pre-migration test data.
 * - The database is then shutdown and restarted (without using
 *   the interceptor this time), so the full "after" schema
 *   is available.
 * - The dbsvc detects the diffs in the schema and executes the
 *   migration callbacks as part of the startup process.
 * - Your implementation of verifyResults() is called to
 *   allow you to confirm that the migration of your prepared
 *   data went as expected.
 * 
 */
public class DbHostInterfaceRegistrationStatusMigrationTest extends DbSimpleMigrationTestBase {
    
    private final int INSTANCES_TO_CREATE = 10;
    
    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.0", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new HostInterfaceRegistrationStatusMigration());
        }});

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "1.0";
    }

    @Override
    protected String getTargetVersion() {
        return "1.1";
    }

    @Override
    protected void prepareData() throws Exception {
        prepareHostInterfaceData(Initiator.class);
        prepareHostInterfaceData(IpInterface.class);
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyHostInterfaceData(Initiator.class);
        verifyHostInterfaceData(IpInterface.class);
    }

    private void prepareHostInterfaceData(Class<? extends HostInterface> clazz) throws Exception {

        for (int i = 0; i < INSTANCES_TO_CREATE; i++) {
            HostInterface hostInterface = clazz.newInstance();
            hostInterface.setId(URIUtil.createId(clazz));
            hostInterface.setHost(URIUtil.createId(Host.class));
            hostInterface.setRegistrationStatus("UNDEFINED");
            _dbClient.createObject(hostInterface);
        }
        
        List<URI> keys = _dbClient.queryByType(clazz, false);
        int count = 0;       
        for (@SuppressWarnings("unused") URI ignore : keys) {
            count++;
        }
        Assert.assertTrue("Expected " + INSTANCES_TO_CREATE + " prepared " + clazz.getSimpleName() + ", found only " + count, count == INSTANCES_TO_CREATE); 
    }

    private void verifyHostInterfaceData(Class<? extends HostInterface> clazz) throws Exception {
        
        List<URI> keys = _dbClient.queryByType(clazz, false);
        int count = 0;
        Iterator<? extends HostInterface> objs =
                _dbClient.queryIterativeObjects(clazz, keys);
        while (objs.hasNext()) {
            HostInterface hostInterface = objs.next();
            count++;
            Assert.assertNotNull("RegistrationStatus shouldn't be null", hostInterface.getRegistrationStatus());
            Assert.assertEquals("RegistrationStatus should equal REGISTERED", RegistrationStatus.REGISTERED.toString(),
                    hostInterface.getRegistrationStatus());
        }
        Assert.assertTrue("We should still have " + INSTANCES_TO_CREATE + " " + clazz.getSimpleName() + " after migration, not " + count, count == INSTANCES_TO_CREATE);        
    }
}
