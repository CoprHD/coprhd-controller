/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.model.mock;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.text.StrSubstitutor;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.server.impl.DbServiceImpl;
import com.emc.storageos.db.server.impl.SchemaUtil;

public class LocalCassandraService {
    public static final String DEFAULT_CLUSTER_NAME = "StorageOS";
    public static final String DEFAULT_KEYSPACE_NAME = "StorageOS";

    private String configResource = "db-mock.yaml";
    private File rootDir;
    private DbServiceImpl dbService;
    private CoordinatorClient coordinator = new StubCoordinatorClientImpl(URI.create("thrift://localhost:9160"));
    private boolean clean;

    public String getConfigResource() {
        return configResource;
    }

    public void setConfigResource(String configResource) {
        this.configResource = configResource;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public DbServiceImpl getDbService() {
        return dbService;
    }

    public void setDbService(DbServiceImpl dbService) {
        this.dbService = dbService;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public boolean isClean() {
        return clean;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    /**
     * Generates a configuration for running a local cassandra instance.
     * 
     * @param configResource
     *        the configuration resource name.
     * @param rootDir
     *        the root directory for the cassandra instance.
     * @return the configuration location.
     * 
     * @throws IOException
     *         if an I/O error occurs.
     */
    private static String createConfig(String configResource, String rootDir) throws IOException {
        URL configURL = LocalCassandraService.class.getResource(configResource);
        if (configURL == null) {
            throw new IllegalStateException("Could not find " + configResource);
        }

        File path = FileUtils.toFile(configURL);
        String data = FileUtils.readFileToString(path, "UTF-8");

        StrSubstitutor substitutor = new StrSubstitutor(Collections.singletonMap("rootDir", rootDir));
        String contents = substitutor.replace(data);

        File configFile = File.createTempFile("config", ".yaml");
        configFile.deleteOnExit();

        FileUtils.writeStringToFile(configFile, contents, "UTF-8");
        return "file:" + configFile.getAbsolutePath();
    }

    /**
     * Deletes given directory
     * 
     * @param dir
     */
    protected static void cleanDirectory(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                cleanDirectory(file);
            }
            else {
                file.delete();
            }
        }
        dir.delete();
    }

    @PostConstruct
    public void init() throws IOException {
        if (clean && rootDir.isDirectory()) {
            cleanDirectory(rootDir);
        }
        startDb();
    }

    @PreDestroy
    public void stop() {
        if (dbService != null) {
            dbService.stop();
        }
        if (clean) {
            cleanDirectory(rootDir);
        }
    }

    private void startDb() throws IOException {
        ServiceImpl service = new ServiceImpl();
        service.setName("dbsvc");
        service.setEndpoint(URI.create("thrift://localhost:9160"));
        service.setId("foobar");

        DataObjectScanner scanner = new DataObjectScanner();
        scanner.setPackages("com.emc.sa.model");
        scanner.init();

        SchemaUtil util = new SchemaUtil();
        util.setKeyspaceName(DEFAULT_KEYSPACE_NAME);
        util.setClusterName(DEFAULT_CLUSTER_NAME);
        util.setDataObjectScanner(scanner);
        util.setService(service);

        dbService = new DbServiceImpl();
        dbService.setConfig(createConfig(configResource, rootDir.getAbsolutePath()));
        dbService.setSchemaUtil(util);
        dbService.setCoordinator(coordinator);
        dbService.setService(service);
        dbService.start();
    }
}
