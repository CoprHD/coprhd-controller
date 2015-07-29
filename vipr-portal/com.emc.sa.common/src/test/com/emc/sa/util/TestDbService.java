/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.server.impl.DbServiceImpl;
import com.emc.storageos.db.server.impl.SchemaUtil;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * Starts up the ViPR DBSVC for testing
 * 
 * @author dmaddison
 */
public class TestDbService {
    private static Logger LOG = Logger.getLogger(TestDbService.class);

    private CoordinatorClient coordinatorClient;

    private List<String> modelPackages = Lists.newArrayList("com.emc.sa.model");
    private DbServiceImpl dbsvc;

    private static File dataDir = new File("dbtest");

    public TestDbService(TestCoordinatorService coordinatorService) throws Exception {
        this(coordinatorService.getCoordinatorClient());
    }

    public TestDbService(CoordinatorClient coordinatorClient) throws Exception {
        this.coordinatorClient = coordinatorClient;

    }

    public void setDataDir(String dataDir) {
        this.dataDir = new File(dataDir);
    }

    public void addModelPackage(String modelPackage) {
        modelPackages.add(modelPackage);
    }

    /** Starts the Cassandra DB but deletes any existing data files first */
    public void startClean() throws Exception {
        FileUtils.deleteDirectory(dataDir);

        start();
    }

    /** Starts the Cassandra DB, using any Persisted data files */
    public void start() throws Exception {
        if (dbsvc != null) {
            return;
        }

        DataObjectScanner dataObjectScanner = new DataObjectScanner();
        dataObjectScanner.setPackages(modelPackages.toArray(new String[0]));
        dataObjectScanner.init();

        ServiceImpl dummyDBService = new ServiceImpl();
        dummyDBService.setName("dbsvc");
        dummyDBService.setVersion("1");
        dummyDBService.setEndpoint(URI.create("thrift://localhost:9170"));
        dummyDBService.setId("foobar");

        SchemaUtil schemaUtil = new SchemaUtil();
        schemaUtil.setKeyspaceName("Testing");
        schemaUtil.setService(dummyDBService);
        schemaUtil.setCoordinator(coordinatorClient);
        schemaUtil.setDataObjectScanner(dataObjectScanner);

        String config = createConfig(dataDir.getAbsolutePath());
        dbsvc = new DbServiceImpl();
        dbsvc.setConfig(config);
        dbsvc.setSchemaUtil(schemaUtil);
        dbsvc.setCoordinator(coordinatorClient);
        dbsvc.setService(dummyDBService);
        dbsvc.start();
    }

    /** Stops the Database service, deleting all persistent data files */
    public void destroy() throws Exception {
        stop();

        FileUtils.deleteDirectory(dataDir);
    }

    /** Stops the Cassandra DB, keeping all persistent data intact */
    public void stop() throws Exception {
        dbsvc.stop();

        dbsvc = null;
    }

    private static String createConfig(String rootDir) throws IOException {
        URL configURL = TestDbService.class.getResource("db-test.yaml");
        if (configURL == null) {
            throw new IllegalStateException("Could not find cassandra.yaml");
        }

        File path = FileUtils.toFile(configURL);
        String data = FileUtils.readFileToString(path, "UTF-8");

        StrSubstitutor substitutor = new StrSubstitutor(Collections.singletonMap("rootDir", rootDir));
        String contents = substitutor.replace(data);

        File configFile = File.createTempFile("db-test", ".yaml");
        configFile.deleteOnExit();

        FileUtils.writeStringToFile(configFile, contents, "UTF-8");
        return "file:" + configFile.getAbsolutePath();
    }
}
