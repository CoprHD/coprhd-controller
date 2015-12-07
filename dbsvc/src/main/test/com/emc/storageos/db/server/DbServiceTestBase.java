/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server;

import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.common.DbServiceStatusChecker;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.impl.MigrationHandlerImpl;
import com.emc.storageos.db.server.impl.SchemaUtil;
import com.emc.storageos.db.server.upgrade.util.InternalDbService;
import com.emc.storageos.db.server.util.StubBeaconImpl;
import com.emc.storageos.db.server.util.StubCoordinatorClientImpl;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.services.util.JmxServerWrapper;
import com.emc.storageos.services.util.LoggingUtils;
import org.apache.cassandra.config.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.Introspector;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The base class of DB service test
 */
public abstract class DbServiceTestBase {
    static {
        LoggingUtils.configureIfNecessary("dbtest-log4j.properties");
    }
    private static Logger log = LoggerFactory.getLogger(DbServiceTestBase.class);

    protected InternalDbService dbsvc;
    protected DbClientImpl dbClient;

    protected DbVersionInfo dbVersionInfo;

    private static File dataDir;
    protected CoordinatorClient coordinator = new StubCoordinatorClientImpl(
            URI.create("thrift://localhost:9160"));

    protected static void removeDb() throws Exception {
        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // Junit test will be called in single thread by default, it's safe to ignore this violation
        dataDir = new File("./dbtest"); // NOSONAR ("squid:S2444")
        if (dataDir.exists() && dataDir.isDirectory()) {
            cleanDirectory(dataDir);
        }
    }

    /**
     * Deletes given directory
     * 
     * @param dir
     */
    protected static void cleanDirectory(File dir) {
        if (dir == null || dir.listFiles() == null) {
            return;
        }

        File[] files = dir.listFiles();

        for (File file : files) {
            if (file.isDirectory()) {
                cleanDirectory(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }

    /**
     * Start embedded DB
     */
    protected void startDb(String schemaVersion, String extraModelsPkg) throws Exception {
        List<String> packages = new ArrayList<String>();
        packages.add("com.emc.storageos.db.client.model");
        if (extraModelsPkg != null) {
            packages.add(extraModelsPkg);
        }
        String[] pkgsArray = packages.toArray(new String[packages.size()]);

        ServiceImpl service = new ServiceImpl();
        service.setName("dbsvc");
        service.setVersion(schemaVersion);
        service.setEndpoint(URI.create("thrift://localhost:9160"));
        service.setId("db-standalone");

        DataObjectScanner scanner = new DataObjectScanner();
        scanner.setPackages(pkgsArray);
        scanner.init();

        dbVersionInfo = new DbVersionInfo();
        dbVersionInfo.setSchemaVersion(schemaVersion);

        coordinator.setDbVersionInfo(dbVersionInfo);

        DbServiceStatusChecker statusChecker = new DbServiceStatusChecker();
        statusChecker.setCoordinator(coordinator);
        statusChecker.setClusterNodeCount(1);
        statusChecker.setDbVersionInfo(dbVersionInfo);
        statusChecker.setServiceName(service.getName());

        CoordinatorClientInetAddressMap coordinatorMap = new CoordinatorClientInetAddressMap();
        coordinatorMap.setNodeId("localhost");
        coordinatorMap.setDualInetAddress(DualInetAddress.fromAddress("127.0.0.1"));
        Map<String, DualInetAddress> addressLookupMap = new HashMap<String, DualInetAddress>();
        addressLookupMap.put(coordinatorMap.getNodeId(), coordinatorMap.getDualInetAddress());
        coordinatorMap.setControllerNodeIPLookupMap(addressLookupMap);
        coordinatorMap.setCoordinatorClient(coordinator);
        coordinator.setInetAddessLookupMap(coordinatorMap);

        SchemaUtil util = new SchemaUtil();
        util.setKeyspaceName("Test");
        util.setClusterName("Test");
        util.setDataObjectScanner(scanner);
        util.setService(service);
        util.setStatusChecker(statusChecker);
        util.setCoordinator(coordinator);
        util.setVdcShortId("vdc1");
        util.setClientContext(createLocalContext());
        List<String> vdcHosts = new ArrayList();
        vdcHosts.add("127.0.0.1");
        util.setVdcNodeList(vdcHosts);
        util.setDbCommonInfo(new java.util.Properties());

        dbsvc = new InternalDbService();
        dbsvc.setConfig("db-test.yaml");
        dbsvc.setSchemaUtil(util);
        dbsvc.setCoordinator(coordinator);
        dbsvc.setStatusChecker(statusChecker);
        dbsvc.setService(service);
        dbsvc.setDbDir(".");

        JmxServerWrapper jmx = new JmxServerWrapper();
        jmx.setEnabled(false);

        dbsvc.setJmxServerWrapper(jmx);

        dbClient = getDbClientBase();

        dbsvc.setDbClient(dbClient);

        PasswordUtils passwordUtils = new PasswordUtils();
        passwordUtils.setCoordinator(coordinator);
        EncryptionProviderImpl provider = new EncryptionProviderImpl();
        provider.setCoordinator(coordinator);
        provider.start();
        passwordUtils.setEncryptionProvider(provider);
        passwordUtils.setDbClient(dbClient);
        util.setPasswordUtils(passwordUtils);

        StubBeaconImpl beacon = new StubBeaconImpl(service);
        dbsvc.setBeacon(beacon);

        MigrationHandlerImpl handler = new MigrationHandlerImpl();
        handler.setPackages(pkgsArray);
        handler.setService(service);
        handler.setStatusChecker(statusChecker);
        handler.setCoordinator(coordinator);
        handler.setDbClient(dbClient);
        handler.setSchemaUtil(util);

        dbsvc.setMigrationHandler(handler);
        dbsvc.setDisableScheduledDbRepair(true);

        dbsvc.start();
    }

    protected DbClientImpl getDbClientBase() {
        InternalDbClient dbClient = new InternalDbClient();
        dbClient.setCoordinatorClient(coordinator);
        dbClient.setDbVersionInfo(dbVersionInfo);
        dbClient.setBypassMigrationLock(true);
        dbClient.setLocalContext(createLocalContext());
        VdcUtil.setDbClient(dbClient);
        
        return dbClient;
    }

    protected DbClientContext createLocalContext() {
        DbClientContext context = new DbClientContext();
        context.setClusterName("Test");
        context.setKeyspaceName("Test");
        return context;
    }

    protected void stopDb() {
        TypeMap.clear();
        Introspector.flushCaches();

        if (dbsvc != null) {
            dbsvc.stop();
            Schema.instance.clear();
            log.info("The dbsvc is stopped");
        }
    }

    protected static boolean isPreMigration() {
        return Boolean.parseBoolean(System.getProperty("preMigration"));
    }
}
