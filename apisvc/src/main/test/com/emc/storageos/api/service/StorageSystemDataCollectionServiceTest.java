/*
 * Copyright (c) 2011-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import com.emc.storageos.api.service.impl.resource.StorageSystemDataCollectionService;
import com.emc.storageos.model.collectdata.ScaleIOCollectDataParam;
import com.emc.storageos.model.collectdata.ScaleIOSystemDataRestRep;
import com.emc.storageos.scaleio.api.ScaleIORestClientTest;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIODevice;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSDS;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSystem;
import com.emc.storageos.services.util.EnvConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * FilePolicyTest class to exercise the core api functionality of File Policy Service.
 */

@RunWith(MockitoJUnitRunner.class)
public class StorageSystemDataCollectionServiceTest extends ApiTestBase {

    private static Logger log = LoggerFactory.getLogger(ScaleIORestClientTest.class);
    private static StorageSystemDataCollectionService service;
    private static ScaleIORestClientFactory factory;
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String HOST = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.ipaddress");
    private static final String USER = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.user");
    private static final String PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "scaleio.host.api.password");
    private static int PORT = 443;

    @BeforeClass
    static public void setUp() {
        service = new StorageSystemDataCollectionService();
        factory = new ScaleIORestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
    }

    @Test
    public void testCollectData() {
        service.setScaleIORestClientFactory(factory);
        ScaleIOCollectDataParam param = new ScaleIOCollectDataParam();
        param.setIPAddress(HOST);
        param.setPassword(PASSWORD);
        param.setPortNumber(PORT);
        param.setUserName(USER);
        ScaleIOSystemDataRestRep sio = service.discoverScaleIO(param);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(sio));
    }

    @Mock
    private ScaleIORestClientFactory mockFactory;

    @Mock
    private ScaleIORestClient restClient;

    @Mock
    private ScaleIOCollectDataParam sioParam;

    @Mock
    private ScaleIOSystem sioSystem;

    @Mock
    private ScaleIOSDS sds;

    @Mock
    private ScaleIODevice device;

    @Test
    /*
     * No exception when ScaleIOSDS is null
     */
    public void testDiscoverNullScaleIOSDS() throws Exception {
        Mockito.when(restClient.getSystem()).thenReturn(sioSystem);
        Mockito.when(mockFactory.getRESTClient(Mockito.any(URI.class), Mockito.anyString(), Mockito.anyString())).thenReturn(restClient);

        service.setScaleIORestClientFactory(mockFactory);
        service.discoverScaleIO(sioParam);
    }

    @Test
    /*
     * No exception when ScaleIODevice is null
     */
    public void testDiscoverNullScaleIODevice() throws Exception {
        sds.setId("1");
        List<ScaleIOSDS> sdslist = new ArrayList<ScaleIOSDS>(){{add(sds);}};

        Mockito.when(restClient.queryAllSDS()).thenReturn(sdslist);
        Mockito.when(restClient.getSystem()).thenReturn(sioSystem);
        Mockito.when(mockFactory.getRESTClient(Mockito.any(URI.class), Mockito.anyString(), Mockito.anyString())).thenReturn(restClient);

        service.setScaleIORestClientFactory(mockFactory);
        service.discoverScaleIO(sioParam);

    }

    @Test
    /*
     * No exception when ScaleIOStoragePool is null
     */
    public void testDiscoverNullScaleIOPool() throws Exception {
        sds.setId("1");
        List<ScaleIOSDS> sdslist = new ArrayList<ScaleIOSDS>(){{add(sds);}};
        List<ScaleIODevice> devices = new ArrayList<ScaleIODevice>(){{add(device);}};

        Mockito.when(restClient.queryAllSDS()).thenReturn(sdslist);
        Mockito.when(restClient.getSystem()).thenReturn(sioSystem);
        Mockito.when(restClient.queryAllSDS()).thenReturn(sdslist);
        Mockito.when(restClient.getSdsDevices(Mockito.anyString())).thenReturn(devices);
        Mockito.when(mockFactory.getRESTClient(Mockito.any(URI.class), Mockito.anyString(), Mockito.anyString())).thenReturn(restClient);

        service.setScaleIORestClientFactory(mockFactory);
        service.discoverScaleIO(sioParam);
    }
}
