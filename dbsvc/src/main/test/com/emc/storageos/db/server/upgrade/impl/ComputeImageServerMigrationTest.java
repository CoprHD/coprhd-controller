/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ComputeImageServerMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Template class for writing a migration unit test case.
 * 
 * Copy this file into a classname appropriate for your test and supply an
 * appropriate implementation at the //TODO: markers
 * 
 * Here's the basic execution flow for the test case: - setup() runs, bringing
 * up a "pre-migration" version of the database. Also initializes the list of
 * custom migration callbacks that will be executed later. - Your implementation
 * of prepareData() is called, allowing you to use the internal _dbClient
 * reference to create any needed pre-migration test data. - The database is
 * then shutdown and restarted with the target schema version. - The dbsvc
 * detects the diffs in schema version and executes the migration callbacks as
 * part of the startup process. - Your implementation of verifyResults() is
 * called to allow you to confirm that the migration of your prepared data went
 * as expected.
 */
public class ComputeImageServerMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory
            .getLogger(ComputeImageServerMigrationTest.class);

    @BeforeClass
    public static void setup() throws IOException {

        /**
         * Define a custom migration callback map. The key should be the source
         * version from getSourceVersion(). The value should be a list of
         * migration callbacks under test.
         */
        customMigrationCallbacks.put("2.3",
                new ArrayList<BaseCustomMigrationCallback>() {
                    {
                        // custom implementation of migration callback.
                        add(new ComputeImageServerMigration());
                    }
                });

        DbsvcTestBase.setup();
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
        log.info("Storing image server info into zk configuration");
        Map<String, String> imageServerProps = new HashMap<String, String>();
        imageServerProps.put("image_server_address", "10.247.84.185");
        imageServerProps.put("image_server_username", "root");
        imageServerProps.put("image_server_password", "ChangeMe");
        imageServerProps.put("image_server_tftpbootdirectory", "");
        imageServerProps.put("image_server_os_network_ip", "12.0.6.10");
        imageServerProps.put("image_server_http_port", "");
        imageServerProps.put("image_server_image_directory", "");
        String str = new PropertyInfoExt(imageServerProps).encodeAsString();

        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(PropertyInfoExt.TARGET_PROPERTY);
        config.setId(PropertyInfoExt.TARGET_PROPERTY_ID);
        config.setConfig(PropertyInfoExt.TARGET_INFO, str);

        _coordinator.persistServiceConfiguration(_coordinator.getSiteId(), config);

        String imageServerIP = _coordinator.getPropertyInfo().getProperty(
                "image_server_address");
        if (imageServerIP == null) {
            log.info("image server info not saved to ZK db");
        }

    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> imageServerURIs = _dbClient.queryByType(
                ComputeImageServer.class, true);
        Iterator<ComputeImageServer> iterator = _dbClient
                .queryIterativeObjects(ComputeImageServer.class,
                        imageServerURIs);
        if (iterator.hasNext()) {
            ComputeImageServer imageServer = iterator.next();
            Assert.assertEquals(imageServer.getImageServerIp(), "10.247.84.185");
        } else {
            Assert.fail("compute image server not found in cassandra db");
        }

    }
}
