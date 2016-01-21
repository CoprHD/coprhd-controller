/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.service;

import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.crypto.SecretKey;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.geomodel.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.security.x509.CertAndKeyGen;
import sun.security.x509.X500Name;

import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.TestGeoObject;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenterInUse;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.geo.service.impl.util.VdcConfigHelper;
import com.emc.storageos.geo.vdccontroller.impl.InternalDbClient;
import com.emc.storageos.geo.vdccontroller.impl.VdcControllerImpl;
import com.emc.storageos.geo.vdccontroller.impl.VdcOperationLockHelper;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.security.SignatureHelper;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.security.geo.GeoServiceHelper;
import com.emc.storageos.security.geo.exceptions.FatalGeoException;
import com.emc.storageos.security.geo.exceptions.GeoException;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.vipr.model.sys.ClusterInfo;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

/**
 * Unit test for VdcControllerImpl with mock object
 */
public class VdcControllerTest {
    private final static Logger log = LoggerFactory.getLogger(VdcControllerTest.class);

    VdcControllerImpl vdcController;
    MockDbClient dbClient;
    MockGeoClientCacheManager clientManager;
    KeyStore keystore;
    X509Certificate[] chain;
    PrivateKey privKey;
    char[] password;

    @BeforeClass
    public static void setupAll() throws Exception {
        ProductName name = new DummyProductName("vipr");

        // Listening on port 4443 so that connectivity test could pass. See VdcConfigHelper.areNodesReachable()
        try {
            final ServerSocket serverSocket = new ServerSocket(4443, 0, InetAddress.getByName("127.0.0.1"));
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        try {
                            Socket sock = serverSocket.accept();
                            log.info("Got socket connection from {}", sock.getRemoteSocketAddress());
                        } catch (Exception ex) {
                            log.error("Connectivity check error ", ex);
                        }
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        } catch (BindException e) {
            // Address is already in use, ignore
        }
    }

    @Before
    public void setup() throws Exception {
        vdcController = new VdcControllerImpl();

        MockCoordinatorClient coordinator = new MockCoordinatorClient();

        // setup mock objects for vdc controller test
        dbClient = new MockDbClient();
        vdcController.setDbClient(dbClient);

        InternalApiSignatureKeyGenerator secretKeyGenerator = new InternalApiSignatureKeyGenerator() {
            public synchronized void loadKeys() {

            }

            public SecretKey getSignatureKey(SignatureKeyType type) {
                return SignatureHelper.createKey("test", InternalApiSignatureKeyGenerator.CURRENT_INTERVDC_API_SIGN_ALGO);
            }
        };

        clientManager = new MockGeoClientCacheManager(coordinator, secretKeyGenerator);
        vdcController.setGeoClientManager(clientManager);
        vdcController.setVdcOperationLockHelper(new MockVdcOperationLockHelper());
        keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        password = "some password".toCharArray();
        keystore.load(null, password);
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512, new SecureRandom());
        CertAndKeyGen keypair = new CertAndKeyGen("RSA", "SHA1WithRSA", null);
        X500Name x500Name = new X500Name("EMC", "EMC", "EMC", "EMC", "MA", "US");
        keypair.generate(512);
        PrivateKey privKey = keypair.getPrivateKey();
        chain = new X509Certificate[1];
        chain[0] = keypair.getSelfCertificate(x500Name, new Date(), (long) 365 * 24 * 60 * 60);
        keystore.setKeyEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, privKey, password, chain);
        vdcController.setKeystore(keystore);
        vdcController.setSignatureGenerator(secretKeyGenerator);

        BasePermissionsHelper permissionsHelper = new BasePermissionsHelper(dbClient);
        vdcController.setPermissionsHelper(permissionsHelper);

        // Setup helper based on mocked db client
        VdcConfigHelper helper = new VdcConfigHelper();
        helper.setDbClient(dbClient);
        helper.setGeoClientCacheManager(clientManager);
        helper.setCoordinatorClient(coordinator);
        vdcController.setVdcHelper(helper);
        VdcUtil.setDbClient(dbClient);
        dbClient.buildGeodbData();

    }

    /**
     * Adding vdc2 to vdc1
     */
    // Test hanging in IDE and "gradlew test"
    @Test
    public void testAddToSingleVdc() throws Exception {
        dbClient.buildInitData(1);
        // Treat as Geo strategy options already set to remove Cassandra, preventing calling to
        // remote JMX
        dbClient.getGeoStrategyOptions().put("vdc2", "abc");
        VirtualDataCenter vdc1 = dbClient.vdcList.get(0);
        VirtualDataCenter newVdc = newVdcForAdding("vdc2");
        // dbClient.vdcList.add(newVdc);
        log.info("Testing connect vdc2 {} to vdc1 {}", newVdc.getShortId(), vdc1.getId());

        Properties vdcInfo = GeoServiceHelper.getVDCInfo(newVdc);

        String reqId = "taskid-0000";
        addTask(reqId, vdc1.getId());

        // Start execute vdc connect
        vdcController.connectVdc(vdc1, reqId, Arrays.asList(new Object[] { vdcInfo }));

        // Verify result
        URI newVDCId = null;
        Iterator<URI> vdcIter = dbClient.queryByType(VirtualDataCenter.class, true).iterator();
        while (vdcIter.hasNext()) {
            URI id = vdcIter.next();
            String vdcId = URIUtil.parseVdcIdFromURI(id.toASCIIString());
            if (vdcId.equals("vdc2")) {
                newVDCId = id;
                break;
            }
        }
        Assert.assertNotNull(newVDCId);
        VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, newVDCId);
        Assert.assertNotNull(vdc);
        Assert.assertTrue(vdc.getShortId().equals("vdc2"));
        Assert.assertNotNull(vdc.getHostCount());
        Assert.assertNotNull(vdc.getHostIPv4AddressesMap());
        Assert.assertTrue(vdc.getHostIPv4AddressesMap().size() == 1);
        Assert.assertTrue(clientManager.client.countForSyncCall == 1);
    }

    /**
     * Adding vdc4 to a connected vdc1/2/3
     */
    // Test hanging in IDE and "gradlew test"
    @Test
    public void testAddToMultipleVdc() throws Exception {
        // create a mockdb with 3 existing vdc
        dbClient.buildInitData(3);
        // Treat as Geo strategy options already set to remove Cassandra, preventing calling to
        // remote JMX
        dbClient.getGeoStrategyOptions().put("vdc4", "abc");
        VirtualDataCenter vdc1 = dbClient.vdcList.get(0);
        VirtualDataCenter newVdc = newVdcForAdding("vdc4");
        // dbClient.vdcList.add(newVdc);

        log.info("Testing connect new vdc {} to 3 existing vdc {}", newVdc.getShortId(), vdc1.getId());

        Properties vdcInfo = GeoServiceHelper.getVDCInfo(newVdc);
        String reqId = "taskid-0001";
        addTask(reqId, vdc1.getId());

        // Start execute vdc connect
        vdcController.connectVdc(vdc1, reqId, Arrays.asList(new Object[] { vdcInfo }));

        // Verify result
        URI newVDCId = null;
        Iterator<URI> vdcIter = dbClient.queryByType(VirtualDataCenter.class, true).iterator();
        while (vdcIter.hasNext()) {
            URI id = vdcIter.next();
            String vdcId = URIUtil.parseVdcIdFromURI(id.toASCIIString());
            if (vdcId.equals("vdc4")) {
                newVDCId = id;
                break;
            }
        }
        Assert.assertNotNull(newVDCId);
        VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, newVDCId);
        Assert.assertNotNull(vdc);
        Assert.assertTrue(vdc.getShortId().equals("vdc4"));
        Assert.assertNotNull(vdc.getHostCount());
        Assert.assertNotNull(vdc.getHostIPv4AddressesMap());
        Assert.assertTrue(clientManager.client.countForSyncCall == 3);
    }

    // Test hanging in IDE and "gradlew test"
    @Test
    public void testAddVdcPrecheckFailure() throws Exception {
        // create a mock db with 2 existing vdc
        dbClient.buildInitData(2);
        VirtualDataCenter vdc1 = dbClient.vdcList.get(0);
        vdc1.setConnectionStatus(VirtualDataCenter.ConnectionStatus.CONNECTING);
        VirtualDataCenter newVdc = newVdcForAdding("vdc2");

        log.info("Testing connect vdc2 {} to vdc1 {}", newVdc.getShortId(), vdc1.getId());

        // Start execute vdc connect
        try {
            Properties vdcInfo = GeoServiceHelper.getVDCInfo(newVdc);
            String reqId = "taskid-0003";
            addTask(reqId, vdc1.getId());
            // Start execute vdc connect
            vdcController.connectVdc(vdc1, reqId, Arrays.asList(new Object[] { vdcInfo }));
            Assert.assertTrue("Precheck should throw an exception", false);
        } catch (Exception ex) {
            log.error("precheck error ", ex);
            Assert.assertTrue(ex instanceof FatalGeoException);
        }
    }

    /**
     * Remove vdc2 from vdc1
     */
    // Test hanging in IDE and "gradlew test"
    @Test
    public void testRemoveVdc() throws Exception {
        // create a mock db with 2 existing vdc
        dbClient.buildInitData(2);
        VirtualDataCenter vdc1 = dbClient.vdcList.get(0);
        VirtualDataCenter vdc2 = dbClient.vdcList.get(1);

        log.info("Testing remove vdc2 {} from vdc1 {}", vdc2.getId(), vdc1.getId());

        // Start execute vdc remove
        vdc2.setConnectionStatus(VirtualDataCenter.ConnectionStatus.REMOVING);
        dbClient.updateAndReindexObject(vdc2);
        String reqId = "remove-taskid-0000";
        addTask(reqId, vdc2.getId());
        vdcController.removeVdc(vdc2, reqId, null);

        // Verify result
        Assert.assertTrue(dbClient.vdcList.size() == 1);
        VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdc1.getId());
        Assert.assertNotNull(vdc);
        Assert.assertTrue(vdc.getShortId().equals("vdc1"));
        Assert.assertNotNull(vdc.getHostCount());
        Assert.assertNotNull(vdc.getHostIPv4AddressesMap());
        Assert.assertTrue(clientManager.client.countForSyncCall == 1);

        // vdc2 should be removed
        vdc = dbClient.queryObject(VirtualDataCenter.class, vdc2.getId());
        Assert.assertNull(vdc);
    }

    /**
     * Remove vdc2 from vdc1
     */
    // Test hanging in IDE and "gradlew test"
    @Test
    public void testRemoveVdcPreCheck() throws Exception {
        // create a mock db with 2 existing vdc
        dbClient.buildInitData(2);
        VirtualDataCenter vdc1 = dbClient.vdcList.get(0);
        VirtualDataCenter vdc2 = dbClient.vdcList.get(1);

        log.info("Testing precheck for removing vdc2 {} from vdc1 {}", vdc2.getId(), vdc1.getId());

        // create a geo object referencing a geo visible object in vdc2
        TestGeoObject obj = new TestGeoObject();
        obj.setId(URIUtil.createId(TestGeoObject.class));
        String varrayId = URIUtil.createId(VirtualArray.class).toString();
        varrayId = varrayId.replace("vdc1", "vdc2");
        obj.setVarray(new URI(varrayId));
        dbClient.testGeoList.add(obj);

        // Start execute vdc remove
        try {
            String reqId = "remove-taskid-0002";
            addTask(reqId, vdc2.getId());
            vdcController.removeVdc(vdc2, reqId, null);
            Assert.assertTrue("Precheck should throw an exception", false);
        } catch (Exception ex) {
            log.error("precheck error ", ex);
            Assert.assertTrue(ex instanceof FatalGeoException);
        }

    }

    /**
     * Remove vdc2 from vdc1
     */
    // Test hanging in IDE and "gradlew test"
    @Test
    public void testRemoveVdcInUsePreCheck() throws Exception {
        // create a mock db with 2 existing vdc
        dbClient.buildInitData(2);
        VirtualDataCenter vdc1 = dbClient.vdcList.get(0);
        VirtualDataCenter vdc2 = dbClient.vdcList.get(1);

        log.info("Testing precheck for removing vdc2 {} from vdc1 {}", vdc2.getId(), vdc1.getId());

        VirtualDataCenterInUse vdcInUse = new VirtualDataCenterInUse();
        vdcInUse.setId(vdc2.getId());
        vdcInUse.setInUse(true);
        dbClient.createObject(vdcInUse);

        // Start execute vdc remove
        try {
            String reqId = "remove-taskid-0003";
            addTask(reqId, vdc2.getId());
            vdcController.removeVdc(vdc2, reqId, null);
            Assert.assertTrue("Precheck should throw an exception", false);
        } catch (Exception ex) {
            log.error("precheck error ", ex);
            Assert.assertTrue(ex instanceof FatalGeoException);
        }
    }

    /**
     * Remove vdc3 from vdc1/vdc2
     */
    // Test hanging in IDE and "gradlew test"
    @Test
    public void testRemoveVdcFromThreeSiteGeo() throws Exception {
        // create a mock db with 3 existing vdc
        dbClient.buildInitData(3);
        VirtualDataCenter vdc1 = dbClient.vdcList.get(0);
        VirtualDataCenter vdc2 = dbClient.vdcList.get(1);
        VirtualDataCenter vdc3 = dbClient.vdcList.get(1);
        log.info("Testing remove vdc3 {} ", vdc3.getId());

        // Start execute vdc remove
        vdc3.setConnectionStatus(VirtualDataCenter.ConnectionStatus.REMOVING);
        dbClient.updateAndReindexObject(vdc3);
        String reqId = "remove-taskid-0003";
        addTask(reqId, vdc3.getId());
        vdcController.removeVdc(vdc3, reqId, null);

        // Verify result
        Assert.assertTrue(dbClient.vdcList.size() == 2);
        VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdc1.getId());
        Assert.assertNotNull(vdc);
        Assert.assertTrue(vdc.getShortId().equals("vdc1"));
        Assert.assertNotNull(vdc.getHostCount());
        Assert.assertNotNull(vdc.getHostIPv4AddressesMap());
        Assert.assertTrue(clientManager.client.countForSyncCall == 2);

        // vdc3 should be removed from local db
        vdc = dbClient.queryObject(VirtualDataCenter.class, vdc3.getId());
        Assert.assertNull(vdc);
    }

    /**
     * Copy VDC attributes from one object to another
     * 
     * @param src
     * @param dest
     */
    private void copyVdcObject(VirtualDataCenter src, VirtualDataCenter dest) {
        log.info("Copy object {}", dest.getId());
        dest.setShortId(src.getShortId());
        dest.setVersion(src.getVersion());
        dest.setApiEndpoint(src.getApiEndpoint());
        dest.setConnectionStatus(src.getConnectionStatus());
        dest.setLocal(src.getLocal());
        dest.setHostCount(src.getHostCount());
        dest.setHostIPv4AddressesMap(src.getHostIPv4AddressesMap());
        dest.setHostIPv6AddressesMap(src.getHostIPv6AddressesMap());
        Assert.assertNotNull(src.getHostIPv4AddressesMap());
    }

    /**
     * Dump VirtualDataCenter object to log
     * 
     * @param vdc
     */
    private void dumpVdcObject(VirtualDataCenter vdc) {
        log.info("VDC id = {}, ", vdc.getId());
        log.info("    shortId {}", vdc.getShortId());
        log.info("    version {}", vdc.getVersion());
        log.info("    hostCount {}", vdc.getHostCount());
        log.info("    local {}", vdc.getLocal());
        log.info("    status {}", vdc.getConnectionStatus());
    }

    /**
     * New Vdc object for adding
     * 
     * @return
     */
    private VirtualDataCenter newVdcForAdding(String shortId) {
        VirtualDataCenter vdc = new VirtualDataCenter();
        URI vdcId = URIUtil.createVirtualDataCenterId(shortId);
        String vdcShortId = shortId;
        vdc.setShortId(vdcShortId);
        vdc.setId(vdcId);
        vdc.setVersion(2L);
        vdc.setLocal(false);
        vdc.setApiEndpoint("127.0.0.2");
        vdc.setHostCount(1);
        vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.CONNECTING);
        vdc.setRepStatus(VirtualDataCenter.GeoReplicationStatus.REP_NONE);
        vdc.setCertificateChain(TEST_CERT);
        return vdc;
    }

    private void addTask(String requestId, URI vdcId) {
        Task task = new Task();

        task.setId(URIUtil.createId(Task.class));
        task.setRequestId(requestId);
        NamedURI resource = new NamedURI(vdcId, "vdc");
        task.setResource(resource);
        this.dbClient.createObject(task);
    }

    // Mock for dbclient who stores vdc config in memory
    class MockDbClient extends InternalDbClient {
        List<VirtualDataCenter> vdcList;
        URI rootTenantId = URIUtil.createId(TenantOrg.class);
        List<TestGeoObject> testGeoList;
        Map<Class, List> inMemDb;
        Map<String, String> geoStrategyOptions;

        MockDbClient() {
            inMemDb = new HashMap<Class, List>();
            testGeoList = new ArrayList<TestGeoObject>();
            vdcList = new ArrayList<VirtualDataCenter>();
            geoStrategyOptions = new HashMap<String, String>();
        }

        void buildInitData(int numOfVdc) throws Exception {
            // create a initial list for vdc config
            for (int i = 1; i < numOfVdc + 1; i++) {
                VirtualDataCenter vdc = new VirtualDataCenter();
                String vdcShortId = "vdc" + i;
                URI vdcId = URIUtil.createVirtualDataCenterId(vdcShortId);
                vdc.setShortId(vdcShortId);
                vdc.setId(vdcId);
                vdc.setVersion(2L);
                vdc.setLocal(i == 1);
                vdc.setHostCount(1);
                vdc.setRepStatus(VirtualDataCenter.GeoReplicationStatus.REP_ALL);
                StringMap hostList = new StringMap();
                hostList.put("node1", "127.0.0.1");
                vdc.setHostIPv4AddressesMap(hostList);
                vdc.setApiEndpoint("127.0.0.2");
                vdc.setCertificateChain(TEST_CERT);
                vdcList.add(vdc);
                if (numOfVdc > 1) {
                    vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.CONNECTED);
                } else {
                    vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.ISOLATED);
                }
                keystore.setCertificateEntry(vdcId.toString(), chain[0]);
            }

            inMemDb.put(TestGeoObject.class, testGeoList);
            inMemDb.put(VirtualDataCenter.class, vdcList);
            inMemDb.put(VirtualDataCenterInUse.class, new ArrayList<VirtualDataCenterInUse>());
        }

        void buildGeodbData() {
            TestGeoObject obj = new TestGeoObject();
            obj.setId(URIUtil.createId(TestGeoObject.class));
            obj.setVarray(URIUtil.createId(VirtualArray.class));
            obj.setVpool(URIUtil.createId(VirtualPool.class));
            testGeoList.add(obj);
        }

        private <T extends DataObject> List<T> getObjectList(Class<T> clazz) {
            List<T> objList = inMemDb.get(clazz);
            if (objList == null) {
                objList = new ArrayList<T>();
                inMemDb.put(clazz, objList);
            }
            return objList;
        }

        @Override
        public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly) throws DatabaseException {
            log.info("queryByType in mock db client for {}", clazz);
            List<URI> result = new ArrayList<URI>();
            List<T> objList = getObjectList(clazz);
            for (T obj : objList) {
                if (activeOnly && obj.getInactive().booleanValue()) {
                    continue;
                }
                result.add(obj.getId());
            }

            return result;
        }

        @Override
        public <T extends DataObject> T queryObject(Class<T> clazz, URI id) {
            log.info("queryObject in mock db client for {}", id);
            List<T> objList = getObjectList(clazz);
            for (T obj : objList) {
                if (id.equals(obj.getId())) {
                    log.info("queryObject {}", obj.getId());
                    return obj;
                }
            }
            return null;
        }

        @Override
        public void removeVdcNodesFromBlacklist(VirtualDataCenter vdc) {
        }

        @Override
        public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids, boolean activeOnly)
                throws DatabaseException {
            List<T> result = new ArrayList<T>();
            Iterator<URI> iter = ids.iterator();
            while (iter.hasNext()) {
                URI uri = iter.next();
                result.add(queryObject(clazz, uri));
            }
            return result;
        }

        @Override
        public <T extends DataObject> List<T> queryObjectField(Class<T> clazz, String fieldName, Collection<URI> ids)
                throws DatabaseException {
            log.info("queryObjectField in mock db client");
            return queryObject(clazz, ids, true);
        }

        @Override
        public <T extends DataObject> Collection<T> queryObjectFields(Class<T> clazz,
                Collection<String> fieldNames, Collection<URI> ids) {
            log.info("queryObjectFields in mock db client");
            return queryObject(clazz, ids, true);
        }

        @Override
        public <T extends DataObject> void createObject(T object) throws DatabaseException {
            log.info("create Object {}", object);
            List<T> objList = getObjectList((Class<T>) object.getClass());
            objList.add(object);
        }

        @Override
        public <T extends DataObject> void updateAndReindexObject(T object) throws DatabaseException {
            log.info("updateAndReindexObject {}", object);

            if (object instanceof VirtualDataCenter) {
                VirtualDataCenter obj = (VirtualDataCenter) object;
                dumpVdcObject(obj);
                for (VirtualDataCenter vdc : vdcList) {
                    if (obj.getId().equals(vdc.getId())) {
                        copyVdcObject(obj, vdc);
                    }
                }
            }
            else {
                // BasePermissionsHelper.removeRootRoleAssignmentOnTenantAndProject may update projects or tenants
                boolean isProject = object instanceof Project;
                boolean isTenant = object instanceof TenantOrg;
                Assert.assertTrue("Unexpected object update", isProject || isTenant);
            }
        }

        public Operation ready(Class<? extends DataObject> clazz, URI id, String opId) throws DatabaseException {
            Operation op = new Operation();
            op.ready();
            op.setProgress(100);
            return op;
        }

        public Operation error(Class<? extends DataObject> clazz, URI id, String opId, ServiceCoded code) throws DatabaseException {
            Operation op = new Operation();
            op.error(code);
            return op;
        }

        public void waitAllSitesDbStable() {
            log.info("waitAllSitesDbStable - ");
        }

        public void waitDbRingRebuildDone(String vdcShortId, int vdcHosts) {
            log.info("waitDbRingRebuildDone - ");
        }

        @Override
        public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result)
                throws DatabaseException {
            Class<? extends DataObject> doType = constraint.getDataObjectType();
            log.info("QueryByConstraint for {}", doType);
            ArrayList<URI> list = new ArrayList<URI>();
            if (doType.getSimpleName().equalsIgnoreCase("TenantOrg")) {
                list.add(rootTenantId);
            } else if (doType.getSimpleName().equalsIgnoreCase("TestGeoObject")) {
                list.add(testGeoList.get(0).getId());
            } else if (doType.getSimpleName().equalsIgnoreCase("Task")) {
                List<URI> tasks = queryByType(Task.class, true);
                log.info("Tasks {}", tasks.size());
                list.addAll(tasks);
            }
            result.setResult((java.util.Iterator<T>) list.iterator());
            // throw new IllegalStateException("Unsupported queryByConstraint for " + doType);
        }

        @Override
        public void removeObject(DataObject... object) throws DatabaseException {
            markForDeletion(object);
        }

        @Override
        public void markForDeletion(Collection<? extends DataObject> objects) throws DatabaseException {
            Iterator<? extends DataObject> it = objects.iterator();
            while (it.hasNext()) {
                DataObject obj = it.next();

                List<? extends DataObject> objList = getObjectList((Class<? extends DataObject>) obj.getClass());
                for (int i = 0; i < objList.size(); i++) {
                    DataObject existingObj = objList.get(i);
                    if (existingObj.getId().equals(obj.getId())) {
                        objList.remove(i);
                        log.info("markForDeletion: remove {}", obj.getId());
                    }
                }
            }
        }

        @Override
        public Map<String, String> getGeoStrategyOptions() throws ConnectionException {
            return this.geoStrategyOptions;
        }

        @Override
        public CoordinatorClient getCoordinatorClient() {
            return new MockCoordinatorClient();
        }

        @Override
        public void stopClusterGossiping() {
            log.info("Stop cluster gossiping");
        }

        public void waitVdcRemoveDone(String vdcShortId) {
        }

        public String getSchemaVersion() {
            return "2.0";
        }

        public boolean isGeoDbClientEncrypted() {
            return false;
        }
    }

    // mock object for GeoServiceClient
    class MockGeoServiceClient extends GeoServiceClient {
        int countForSyncCall = 0;
        int countForApplyCall = 0;
        int countForPostCheckCall = 0;

        public MockGeoServiceClient() {
            setServer("127.0.0.1");
        }

        // called on vdc2 to fetch its information
        @Override
        public VdcPreCheckResponse syncVdcConfigPreCheck(VdcPreCheckParam param, String vdcName) throws GeoException {
            log.info("getVdcConfig on new vdc");
            VdcPreCheckResponse resp = new VdcPreCheckResponse();
            resp.setId(URIUtil.createVirtualDataCenterId("vdc99"));
            resp.setShortId("vdc99");
            resp.setApiEndpoint("127.0.0.2");
            resp.setVersion(1L);
            resp.setHostCount(1);
            StringMap hostList = new StringMap();
            hostList.put("node1", "127.0.0.1");
            resp.setHostIPv4AddressesMap(hostList);
            resp.setApiEndpoint("127.0.0.2");
            resp.setHasData(false);
            resp.setClusterStable(true);
            resp.setSoftwareVersion("vipr-2.3.0.0.100");
            return resp;
        }

        @Override
        public VdcNatCheckResponse vdcNatCheck(VdcNatCheckParam checkParam) {
            VdcNatCheckResponse resp = new VdcNatCheckResponse();
            resp.setBehindNAT(false);
            return resp;
        }

        // called on vdc2 to update Vdc config list
        @Override
        public void syncVdcConfig(VdcConfigSyncParam vdcConfigList, String vdcName) throws GeoException {
            countForSyncCall++;
            log.info("GeoClient: SyncVdcConfig " + countForSyncCall);
        }

        public void applyVdcConfig() throws Exception {
            log.info("GeoClient: applyVdcConfig for config change");
            countForApplyCall++;
        }

        @Override
        public void syncVdcConfigPostCheck(VdcPostCheckParam checkParam, String vdcName) throws GeoException {
            log.info("GeoClient: syncVdcConfigPostCheck");
            countForPostCheckCall++;
        }

        @Override
        public void syncVdcCerts(VdcCertListParam vdcCertListParam, String vdcName) throws GeoException {
            log.info("GeoClient: syncVdcCerts {}", vdcCertListParam);
        }

        @Override
        public VdcNodeCheckResponse vdcNodeCheck(VdcNodeCheckParam checkParam) {
            log.info("VdcNodeCheckResponse:  {}", checkParam);
            VdcNodeCheckResponse resp = new VdcNodeCheckResponse();
            resp.setNodesReachable(true);
            return resp;
        }

    }

    class MockGeoClientCacheManager extends GeoClientCacheManager {
        MockGeoServiceClient client = new MockGeoServiceClient();

        public MockGeoClientCacheManager(CoordinatorClient coordinatorClient, InternalApiSignatureKeyGenerator keyGenerator) {
            super();
            client = new MockGeoServiceClient();
            client.setCoordinatorClient(coordinatorClient);
            client.setKeyGenerator(keyGenerator);
        }

        public GeoServiceClient getGeoClient(String shortVdcId) {
            return client;
        }

        public GeoServiceClient getGeoClient(Properties vdcInfo) {
            return client;
        }

        public GeoServiceClient getNonSharedGeoClient(String shortVdcId) {
            return client;
        }

        public void clearCache() {
            log.info("Invalidate client cache");
        }
    }

    class MockCoordinatorClient extends CoordinatorClientImpl {

        @Override
        public CoordinatorClientInetAddressMap getInetAddessLookupMap() {
            CoordinatorClientInetAddressMap coordinatorClientInetAddressMap = new CoordinatorClientInetAddressMap();
            try {
                coordinatorClientInetAddressMap.setDualInetAddress(DualInetAddress.fromAddress("127.0.0.1"));
            } catch (Exception e) {
                log.debug("Ingore this issue.");
            }
            return coordinatorClientInetAddressMap;
        }

        public String getDbConfigPath(String serviceName) {
            return "/config/dbsvc";
        }

        public List<Configuration> queryAllConfiguration(String kind) throws CoordinatorException {
            ArrayList<Configuration> configList = new ArrayList<Configuration>();
            ConfigurationImpl config = new ConfigurationImpl();
            configList.add(config);
            return configList;
        }

        public Configuration queryConfiguration(String kind, String id) {
            ConfigurationImpl config = new ConfigurationImpl();
            config.setConfig(VdcConfigHelper.ENCRYPTION_CONFIG_KIND, "testEncryptionKey");
            return config;
        }

        public void persistServiceConfiguration(Configuration... config) throws CoordinatorException {
            log.info("Mock coordinator - persistServiceConfiguration {}", config);
        }

        public List<Service> locateAllServices(String name, String version, String tag, String endpointKey)
                throws CoordinatorException {
            log.info("LocateAllServices {}", name);
            ArrayList<Service> result = new ArrayList<Service>();
            return result;
        }

        public ClusterInfo.ClusterState getControlNodesState() {
            return ClusterInfo.ClusterState.STABLE;
        }

        public ClusterInfo.ClusterState getExtraNodesState() {
            return ClusterInfo.ClusterState.STABLE;
        }

        public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz) throws Exception {
            if (RepositoryInfo.class.equals(clazz)) {
                SoftwareVersion current = new SoftwareVersion("vipr-2.3.0.0.100");
                List<SoftwareVersion> versions = new ArrayList<SoftwareVersion>();
                versions.add(current);
                RepositoryInfo info = new RepositoryInfo(current, versions);
                return (T) info;
            }
            throw new IllegalStateException("Unexpected getTargetInfo");
        }

        public PropertyInfo getPropertyInfo() throws CoordinatorException {
            PropertyInfo info = new PropertyInfo();
            Map prop = new HashMap<String, String>();
            prop.put("network_standalone_ipaddr", "127.0.0.1");
            info.getProperties().putAll(prop);
            return info;
        }
    }

    static class DummyProductName extends ProductName {
        public DummyProductName(String name) {
            super.setName(name);
        }
    }

    class MockVdcOperationLockHelper extends VdcOperationLockHelper {
        public MockVdcOperationLockHelper() {
        }

        public void acquire(String vdcShortId) {
            log.info("Acquire global lock for {}", vdcShortId);
        }

        public void release(String vdcShortId) {
            log.info("Release global lock for {}", vdcShortId);
        }
    }

    private static final String TEST_CERT = "-----BEGIN CERTIFICATE-----\nMIIDCTCCAfGgAwIBAgIIaA2AN2akqo0wDQYJKoZIhvcNAQELBQAwFzEVMBMGA1UE\nAxMMMTAuMjQ3Ljk3Ljg5MB4XDTE0MDUwODA3MDczMFoXDTI0MDUwNTA3MDczMFow\nFzEVMBMGA1UEAxMMMTAuMjQ3Ljk3Ljg5MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\nMIIBCgKCAQEAlufPeMLLqOwwnrMasu2zr/RRMYqJzT/Qy+Szqh+nDOy7ZYZoixH8\nxZi/Og2guGO8yd6s7bt0PnAqpwR6xrjt5LTb1+egzIfoA4Yz6/mGQIoDbK65agzL\nisgY9GowCliJ4vCnNEMUC0qdFLNOF2rvF8VpPS4/+CURCw3GUdE4ZizNy2XOpQpF\nV1Ke50Lc42uRX3LOuHYZ4SDIfYuSteWTOgGIevsRyPm0cCFPEiX3R7hbAqGNvTy2\n31oAPSzo/eiMRRNfuPHdJXqFq33epHQ2uS2M+0adXX81hO0VUXu/EUN6BrAesTT0\ntjsPj2AxlHX2LpcLVmS9VPb4W5DE1sBm7QIDAQABo1kwVzAfBgNVHSMEGDAWgBRE\n4ltXnjGKleTZ2/12iynVY8z3qTAVBgNVHREEDjAMhwQK92FZhwQK92FXMB0GA1Ud\nDgQWBBRE4ltXnjGKleTZ2/12iynVY8z3qTANBgkqhkiG9w0BAQsFAAOCAQEAC5vA\navBCVZFY/KxgGEmqv+dKsCs7o2/h7K6ItqyPr0gTVR3pKEGbl8zi0Ol5N4rcDbmY\nWu7VKsKun6gMJ9JIMzdKnPXlell35ZxvSTmagzEID0QZAfW4b/xZHQT1AfskUp00\nhQwpqcXcXPdgE/N45ieiNHnfROC8AIXaGJM8D5GrpX6btcgvEEzgFOsXDCmc260R\nehkqnpKvGmskp1BYKt8G7KbCe6WdfX63ca0YF9SFvtvYH7czjZTmFCt1MH4cHvTX\n7IAvnRiV8MKRifdtVNUtpjrcdAEmp6lgZe0jYFMfUPAb/fCI1vA2ybVKFzR6ixm/\ngzGGLxtQBWK4Nqe2rg==\n-----END CERTIFICATE-----";
}
