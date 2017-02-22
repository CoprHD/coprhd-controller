/*
 * Copyright (c) 2011-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import com.emc.storageos.api.service.impl.resource.StorageSystemDataCollectionService;
import com.emc.storageos.model.collectdata.ScaleIOCollectDataParam;
import com.emc.storageos.model.collectdata.ScaleIOSystemDataRestRep;
import com.emc.storageos.scaleio.api.ScaleIORestClientTest;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.services.util.EnvConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * FilePolicyTest class to exercise the core api functionality of File Policy Service.
 */
public class StorageSystemDataCollectionServiceTest extends ApiTestBase {

    private static Logger log = LoggerFactory.getLogger(ScaleIORestClientTest.class);
    private static StorageSystemDataCollectionService service;
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String HOST = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.ipaddress");
    private static final String USER = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.user");
    private static final String PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.password");
    private static int PORT = 443;

    @BeforeClass
    static public void setUp() {
        service = new StorageSystemDataCollectionService();
        ScaleIORestClientFactory factory = new ScaleIORestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        service.setScaleIORestClientFactory(factory);
    }

    @Test
    public void testCollectData() {

        ScaleIOCollectDataParam param = new ScaleIOCollectDataParam();
        param.setIPAddress(HOST);
        param.setPassword(PASSWORD);
        param.setPortNumber(PORT);
        param.setUserName(USER);
        param.setUseSSL(true);
        ScaleIOSystemDataRestRep sio = service.discoverScaleIO(param);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(sio));
    }
}
