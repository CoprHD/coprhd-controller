/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.db;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.simulators.db.impl.SimulatorDbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.simulators.db.model.*;
import com.emc.storageos.simulators.db.model.Snapshot;
import com.emc.storageos.simulators.impl.ObjectStoreImplDb;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * DB client tests
 */
public class DbClientTest extends DbSvcBase {
    private static final Logger _logger = LoggerFactory.getLogger(DbClientTest.class);

    @Before
    public void setup() throws Exception {
        CoordinatorClient cClient = new StubCoordinatorClientImpl("thrift://localhost:9960");
        setCoordinator(cClient);
        SimulatorDbClient dbClient = new SimulatorDbClient();
        dbClient.setCoordinatorClient(cClient);

        DbVersionInfo dbVersionInfo = new DbVersionInfo();
        dbVersionInfo.setSchemaVersion("1");
        dbClient.setDbVersionInfo(dbVersionInfo);

        dbClient.setClusterName("Simulators");
        dbClient.setKeyspaceName("Simulators");
        setDbClient(dbClient);
        startDb();
    }

    @After
    public void stop() {
        _dbsvc.stop();
    }

    /**
     * Create uri
     *
     * @param prefix    prefix string
     * @param id        unique id
     * @return          uri
     */
    private URI createURI(String prefix, String id) {
        return URI.create(String.format("urn:" + prefix + ":%1$s", id));
    }

    @Test
    public void testAll() throws Exception {
        testDirCreate();
        testListDir();
        testDelDir();
        testCreateQuota();
        testDeleteQuota();
        testCreateSnapshot();
        testDeleteSnapshot();
        testCreateExport();
        testDeleteExport();
        testListQuotas();
        testFileShareExt();
    }

    public void testDirCreate() throws Exception {
        SimulatorDbClient dbClient = getDbClient();
        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.createDir("ifs/test111/test222/test333", true);

        Directory obj = dbClient.queryObject(Directory.class, new URI("urn:dir:ifs/test111/test222"));
        assertEquals(obj.getParent().toString(), "urn:dir:ifs/test111");
        obj = dbClient.queryObject(Directory.class, new URI("urn:dir:ifs/test111/test222/test333"));
        assertEquals(obj.getParent().toString(), "urn:dir:ifs/test111/test222");
        obj = dbClient.queryObject(Directory.class, new URI("urn:dir:ifs/test111"));
        assertEquals(obj.getParent().toString(), "urn:dir:ifs");
    }

    public void testListDir() throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.createDir("ifs/test111/test222/test333", true);
        objectStoreImplDb.createDir("ifs/test111/test223/test444", true);

        ArrayList<String> arr = objectStoreImplDb.listDir("ifs/test111");
        assertEquals(arr.get(0), "test222");
        assertEquals(arr.get(1), "test223");
    }

    public void testDelDir() throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.createDir("ifs/test111/test222/test333", true);
        objectStoreImplDb.createDir("ifs/test111/test223/test444", true);

        objectStoreImplDb.deleteDir("ifs/test111", true);

        assertNull(dbClient.queryObject(Directory.class, new URI("urn:dir:ifs/test111/test222")));
        assertNull(dbClient.queryObject(Directory.class, new URI("urn:dir:ifs/test111/test223")));
    }

    public void testCreateQuota() throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.deleteDir("ifs/test111/test222/test333", true);
        objectStoreImplDb.createDir("ifs/test111/test222/test333", true);
        String id = objectStoreImplDb.createQuota("ifs/test111/test222/test333", 10000L);

        Quota quota
                = dbClient.queryQuotaIndex(createURI("quota", id));
        assertNotNull(quota);
        assertNotNull(dbClient.queryObject(Directory.class, quota.getDirectory()));
    }

    public void testDeleteQuota() throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.deleteDir("ifs/test111/test222/test333", true);
        objectStoreImplDb.createDir("ifs/test111/test222/test333", true);
        String id = objectStoreImplDb.createQuota("ifs/test111/test222/test333", 10000L);

        objectStoreImplDb.deleteQuota(id);

        try {
            assertNull(dbClient.queryQuotaIndex(createURI("quota", id)));
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }

        Directory directory
                = dbClient.queryObject(Directory.class, new URI("urn:dir:ifs/test111/test222/test333"));
        assertEquals(directory.getQuota(), ObjectStoreImplDb._emptyURI);
    }

    public void testCreateSnapshot() throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.createDir("ifs/test112/test222/test333", true);

        String id = objectStoreImplDb.createSnapshot("testpath", "ifs/test112/test222/test333");

        assertNotNull(dbClient.queryObject(Snapshot.class, createURI("snap", id)));
        assertNotNull(dbClient.queryObject(Directory.class, URI.create("urn:dir:ifs/.snapshot/testpath/test112/test222/test333")));
        assertNotNull(dbClient.queryObject(Directory.class, URI.create("urn:dir:ifs/.snapshot/testpath/test112/test222")));

        try {
            // test directory not exit
            objectStoreImplDb.createSnapshot("testpath", "path not exit");
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    public void testDeleteSnapshot() throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.createDir("ifs/test112/test222/test333", true);

        String id = objectStoreImplDb.createSnapshot("testpath", "ifs/test112/test222/test333");

        objectStoreImplDb.deleteSnapshot(id);
        assertNull(dbClient.queryObject(Snapshot.class, createURI("snap", id)));

        try {
            // test snapshot not exist
            objectStoreImplDb.deleteSnapshot(id);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    public void testCreateExport() throws Exception {
        SimulatorDbClient dbClient = getDbClient();
        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);

        IsilonExport export = new IsilonExport();

        ArrayList<String> clients = new ArrayList<String>();
        clients.add("10.1.1.1");
        clients.add("10.1.1.2");
        export.setClients(clients);

        String id = objectStoreImplDb.createExport(export);
        IsilonExport read = objectStoreImplDb.getExport(id);

        assertEquals("10.1.1.1", clients.get(0));
        assertEquals("10.1.1.2", clients.get(1));
    }

    public void testDeleteExport() throws Exception {
        SimulatorDbClient dbClient = getDbClient();
        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);

        IsilonExport export = new IsilonExport();

        ArrayList<String> clients = new ArrayList<String>();
        clients.add("10.1.1.1");
        clients.add("10.1.1.2");
        export.setClients(clients);

        String id = objectStoreImplDb.createExport(export);
        objectStoreImplDb.deleteExport(id);

        try {
            objectStoreImplDb.getExport(id);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    public void testListQuotas() throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.deleteDir("ifs/testListQuotas/1", true);
        objectStoreImplDb.createDir("ifs/testListQuotas/1", true);

        String id1 = objectStoreImplDb.createQuota("ifs/testListQuotas/1", 10000L);
        ArrayList<IsilonSmartQuota> list = objectStoreImplDb.listQuotas(null, 100000);
        int i = 0;
        boolean flg = false;
        while (i < list.size()) {
            if (id1.equals(list.get(i).getId()))
                flg = true;
            i++;
        }

        assertTrue(flg);
    }

    public void testFileShareExt() throws Exception {
        SimulatorDbClient dbClient = getDbClient();

        ObjectStoreImplDb objectStoreImplDb = new ObjectStoreImplDb();
        objectStoreImplDb.setDbClient(dbClient);
        objectStoreImplDb.createDir("ifs/testFileShareExt/sub000/sub001/sub002", true);
        objectStoreImplDb.createDir("ifs/testFileShareExt/sub001/sub002/sub003", true);
        objectStoreImplDb.createDir("ifs/testFileShareExt/sub002/sub003/sub004", true);
        objectStoreImplDb.deleteDir("ifs/testFileShareExt/sub002", true);

        assertEquals(2, objectStoreImplDb.getDirCount("ifs/testFileShareExt", false));
        assertEquals(6, objectStoreImplDb.getDirCount("ifs/testFileShareExt", true));
    }
}
