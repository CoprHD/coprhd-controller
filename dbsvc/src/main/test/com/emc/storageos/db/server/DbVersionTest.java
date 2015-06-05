/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server;

import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.beans.Introspector;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * DB version (db service verion and db schema version) setting tests
 */
public class DbVersionTest {
    static {
        LoggingUtils.configureIfNecessary("dbtest-log4j.properties");
    }

    private static final Logger log = LoggerFactory.getLogger(DbVersionTest.class);

    protected InternalDbService _dbsvc;

    protected DbVersionInfo _dbVersionInfo;

    private File _dataDir;
    private CoordinatorClient _coordinator = new StubCoordinatorClientImpl(
            URI.create("thrift://localhost:9160"));

    @Before
    public void setup() {
        _dbVersionInfo = new DbVersionInfo();
        _dbVersionInfo.setSchemaVersion("1.1");

        _dataDir = new File("./dbtest");
        if (_dataDir.exists() && _dataDir.isDirectory()) {
            cleanDirectory(_dataDir);
        }
    } 

    /**
     * Deletes given directory
     *
     * @param dir
     */
    protected void cleanDirectory(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                cleanDirectory(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }

    @Test
    public void matchVerion() throws Exception {
        // make sure that db schema version is "1.1"
        Assert.assertEquals(_dbVersionInfo.getSchemaVersion(), "1.1");

        // set db service version to "1.0"
        startDb("1.0");

        Assert.assertEquals(_dbVersionInfo.getSchemaVersion(), _dbsvc.getServiceVersion());
    }

    /**
     * Start embedded DB
     */
    protected void startDb(String schemaVersion) throws Exception {
        String[] pkgsArray = { "com.emc.storageos.db.client.model"};

        ServiceImpl service = new ServiceImpl();
        service.setName("dbsvc");
        service.setVersion(schemaVersion);
        service.setEndpoint(URI.create("thrift://localhost:9160"));
        service.setId("foobar");

        DataObjectScanner scanner = new DataObjectScanner();
        scanner.setPackages(pkgsArray);
        scanner.init();

        DbServiceStatusChecker statusChecker = new DbServiceStatusChecker();
        statusChecker.setCoordinator(_coordinator);
        statusChecker.setClusterNodeCount(1);
        statusChecker.setDbVersionInfo(_dbVersionInfo);
        statusChecker.setServiceName(service.getName());

        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("nodeaddrmap-var.xml");

        CoordinatorClientInetAddressMap inetAddressMap = (CoordinatorClientInetAddressMap)ctx.getBean("inetAddessLookupMap");

        if (inetAddressMap == null) {
            log.error("CoordinatorClientInetAddressMap is not initialized. Node address lookup will fail.");
        }

        _coordinator.setInetAddessLookupMap(inetAddressMap);

        DbClientContext dbCtx = new DbClientContext();
        dbCtx.setClusterName("Test");
        dbCtx.setKeyspaceName("Test");

        SchemaUtil util = new SchemaUtil();
        util.setKeyspaceName("Test");
        util.setClusterName("Test");
        util.setDataObjectScanner(scanner);
        util.setService(service);
        util.setStatusChecker(statusChecker);
        util.setCoordinator(_coordinator);
        util.setVdcShortId("vdc1");
        util.setDbCommonInfo(new java.util.Properties());
        util.setClientContext(dbCtx);

        List<String> vdcHosts = new ArrayList();
        vdcHosts.add("127.0.0.1");
        util.setVdcNodeList(vdcHosts);

        _dbsvc = new InternalDbService();
        _dbsvc.setConfig("db-test.yaml");
        _dbsvc.setSchemaUtil(util);
        _dbsvc.setCoordinator(_coordinator);
        _dbsvc.setStatusChecker(statusChecker);
        _dbsvc.setService(service);

        JmxServerWrapper jmx = new JmxServerWrapper();
        jmx.setEnabled(false);

        _dbsvc.setJmxServerWrapper(jmx);

        DbClient dbClient = getDbClientBase();
        _dbsvc.setDbClient((DbClientImpl)dbClient);

        PasswordUtils passwordUtils = new PasswordUtils();
        passwordUtils.setCoordinator(_coordinator);
        passwordUtils.setDbClient(dbClient);
        util.setPasswordUtils(passwordUtils);

        StubBeaconImpl beacon = new StubBeaconImpl(service);
        _dbsvc.setBeacon(beacon);

        MigrationHandlerImpl handler = new MigrationHandlerImpl();
        handler.setPackages(pkgsArray);
        handler.setService(service);
        handler.setStatusChecker(statusChecker);
        handler.setCoordinator(_coordinator);
        handler.setDbClient(dbClient);
        handler.setSchemaUtil(util);

        VdcUtil.setDbClient(dbClient);

        _dbsvc.setMigrationHandler(handler);

        _dbsvc.start();
    }

    protected DbClient getDbClientBase() {
        DbClientImpl dbClient = new InternalDbClient();
        dbClient.setCoordinatorClient(_coordinator);
        dbClient.setDbVersionInfo(_dbVersionInfo);
        dbClient.setBypassMigrationLock(true);

        DbClientContext localCtx = new DbClientContext();
        localCtx.setClusterName("Test");
        localCtx.setKeyspaceName("Test");
        dbClient.setLocalContext(localCtx);

        return dbClient;
    }

    @After
    public void stop() {
        stopAll();

        if(_dataDir!=null){
            cleanDirectory(_dataDir);
            _dataDir=null;
        }

        log.info("The Dbsvc is stopped");
    }

   protected void stopAll() {
        TypeMap.clear();
        Introspector.flushCaches();

        _dbsvc.stop();

        Schema.instance.clear();

        log.info("The dbsvc is stopped");

    }
}
