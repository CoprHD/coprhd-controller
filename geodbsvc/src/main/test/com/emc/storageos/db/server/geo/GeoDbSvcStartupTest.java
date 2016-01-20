/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.geo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.KsDef;
import org.apache.cassandra.tools.NodeProbe;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.common.DbConfigConstants;

/**
 * Unit test for geodbsvc startup flow.
 */
public class GeoDbSvcStartupTest {

    private static String GEODBSVC_CONFIG = "geodbtestgeoalone-conf.xml";

    private static final Logger log = LoggerFactory
            .getLogger(DbClientGeoTest.class);
    private static String HOST = "127.0.0.1"; // host name defined in
                                              // geodbtest-conf.yaml
    private static int JMX_PORT = 7299; // JMX port defined in geodb-jmx-var.xml
    private static int RPC_PORT = 9260; // Thrift rpc port defined in
                                        // geodbtest-conf.yaml

    static volatile DbSvcRunner runner;

    static volatile DbClientImpl dbClient;

    @BeforeClass
    public static void setup() throws Exception {
        runner = new DbSvcRunner(GEODBSVC_CONFIG, Constants.GEODBSVC_NAME);
        runner.startCoordinator();
        // set current schema version for local db. dbclient is depending on it
        setLocalDbCurrentVersion(runner.getCoordinator());
        runner.startInProcess();
        if (!runner.waitUntilStarted(100)) {
            Assert.assertTrue("GeoDbsvc could not started", false);
        }
    }

    @Test
    public void checkDbConfig() throws Exception {
        CoordinatorClient coordinator = runner.getCoordinator();

        // Check dbconfig
        String kind = coordinator.getDbConfigPath(Constants.GEODBSVC_NAME);
        Configuration config = coordinator.queryConfiguration(coordinator.getSiteId(), kind,
                DbSvcRunner.GEOSVC_ID);
        Assert.assertNotNull("No dbconfig found", config);
        String value = config.getConfig(DbConfigConstants.JOINED);
        Assert.assertTrue("dbconfig joined flag is false",
                "true".equalsIgnoreCase(value));

        // Check dbconfig/global
        config = coordinator.queryConfiguration(coordinator.getSiteId(), kind, Constants.GLOBAL_ID);
        Assert.assertNotNull("No dbconfig/global found", config);
        value = config.getConfig(Constants.SCHEMA_VERSION);
        Assert.assertTrue("Unexpected dbconfig/global schemaversion",
                DbSvcRunner.SVC_VERSION.equalsIgnoreCase(value));

        // Check versioned dbconfig
        kind = coordinator.getVersionedDbConfigPath(Constants.GEODBSVC_NAME,
                DbSvcRunner.SVC_VERSION);
        config = coordinator.queryConfiguration(coordinator.getSiteId(), kind, DbSvcRunner.GEOSVC_ID);
        Assert.assertNotNull("No versioned dbconfig found", config);
        value = config.getConfig(DbConfigConstants.INIT_DONE);
        Assert.assertTrue("Unexpected versioned dbconfig initdone",
                "true".equalsIgnoreCase(value));

        log.info("Check db config OK");
    }

    @Test
    public void checkSchema() throws Exception {
        // Check if snitch setup is OK
        NodeProbe cmd = new NodeProbe(HOST, JMX_PORT);
        String dc = cmd.getDataCenter();
        Assert.assertTrue("Unexpected DC name " + dc,
                "vdc1".equalsIgnoreCase(dc));

        // Check schema setup is OK
        TSocket socket = new TSocket(HOST, RPC_PORT);
        TTransport transport = new TFastFramedTransport(socket);
        transport.open();
        try {
            Cassandra.Client client = new Cassandra.Client(new TBinaryProtocol(
                    transport, true, true));
            KsDef def = client.describe_keyspace(DbClientContext.GEO_KEYSPACE_NAME);
            String strategyClass = def.strategy_class;
            log.info("Current strategy class in geodb schema: " + strategyClass);
            Assert.assertTrue("Unexpected strategy class " + strategyClass,
                    strategyClass.contains("NetworkTopologyStrategy"));
            Map<String, String> strategyOptions = def.getStrategy_options();
            Assert.assertTrue(strategyOptions.size() > 0);
        } finally {
            transport.close();
        }
    }

    @Test
    public void testRootTenantExists() throws Exception {
        URIQueryResultList tenants = new URIQueryResultList();
        boolean isRootTenantExists = false;
        int retryCount = 0;
        do {
            getDbClient().queryByConstraint(
                    ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                    tenants);
            isRootTenantExists = tenants.iterator().hasNext();
            if (!isRootTenantExists) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    // Ignore this exception
                }

            }
        } while (!isRootTenantExists && retryCount < 30);

        Assert.assertTrue(isRootTenantExists);
    }

    protected static DbClient getDbClient() throws URISyntaxException, IOException {
        if (dbClient == null) {
            dbClient = new DbClientImpl();
            CoordinatorClient coordinator = runner.getCoordinator();
            dbClient.setCoordinatorClient(coordinator);
            DbVersionInfo dbVersionInfo = new DbVersionInfo();
            dbVersionInfo.setSchemaVersion(DbSvcRunner.SVC_VERSION);
            dbClient.setDbVersionInfo(dbVersionInfo);
            dbClient.setBypassMigrationLock(true);

            EncryptionProviderImpl encryptionProvider = new EncryptionProviderImpl();
            encryptionProvider.setCoordinator(coordinator);
            dbClient.setEncryptionProvider(encryptionProvider);

            EncryptionProviderImpl geoEncryptionProvider = new EncryptionProviderImpl();
            geoEncryptionProvider.setCoordinator(coordinator);
            geoEncryptionProvider.setEncryptId("geoid");
            dbClient.setGeoEncryptionProvider(geoEncryptionProvider);

            DbClientContext geoCtx = new DbClientContext();
            geoCtx.setClusterName("GeoStorageOS");
            geoCtx.setKeyspaceName("GeoStorageOS");
            dbClient.setGeoContext(geoCtx);
            dbClient.start();
        }
        return dbClient;
    }

    private static void setLocalDbCurrentVersion(CoordinatorClient coordinator) throws Exception {
        Configuration config = coordinator.queryConfiguration(coordinator.getSiteId(), Constants.DB_CONFIG,
                Constants.GLOBAL_ID);
        if (config == null) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setId(Constants.GLOBAL_ID);
            cfg.setKind(Constants.DB_CONFIG);
            config = cfg;
        }
        config.setConfig(Constants.SCHEMA_VERSION, DbSvcRunner.SVC_VERSION);
        coordinator.persistServiceConfiguration(coordinator.getSiteId(), config);
    }

    @AfterClass
    public static void teardown() {
        dbClient.stop();
        runner.stop();
    }
}
