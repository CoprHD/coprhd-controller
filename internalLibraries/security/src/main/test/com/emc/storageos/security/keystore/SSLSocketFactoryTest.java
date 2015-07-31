/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

import com.emc.storageos.security.ApplicationContextUtil;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.keystore.impl.KeyCertificateAlgorithmValuesHolder;
import com.emc.storageos.security.keystore.impl.KeyCertificateEntry;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.ssl.ViPRSSLSocketFactory;

/**
 * 
 */
public class SSLSocketFactoryTest {

    private final String coordinatorServer = "coordinator://localhost:2181";
    private final String defaultOvfPropsLocation = "/etc/config.defaults";
    private final String ovfPropsLocation = "/etc/ovfenv.properties";
    private final CoordinatorClientImpl coordinatorClient = new CoordinatorClientImpl();
    private final CoordinatorConfigStoringHelper coordConfigStoringHelper = new CoordinatorConfigStoringHelper(coordinatorClient);

    private KeyStore ks;
    private KeyCertificatePairGenerator gen;
    private KeyCertificateEntry entry;
    private String hostName;
    private TestWebServer webServer;

    @Before
    public void setup() throws Exception {
        ApplicationContextUtil.initContext(System.getProperty("buildType"), ApplicationContextUtil.SECURITY_CONTEXTS);

        List<URI> uri = new ArrayList<URI>();
        uri.add(URI.create(coordinatorServer));
        ZkConnection connection = new ZkConnection();
        connection.setServer(uri);
        connection.build();

        coordinatorClient.setZkConnection(connection);

        CoordinatorClientInetAddressMap map = new CoordinatorClientInetAddressMap();
        map.setNodeName("standalone");
        DualInetAddress localAddress = DualInetAddress.fromAddresses("127.0.0.1", "::1");
        map.setDualInetAddress(localAddress);
        Map<String, DualInetAddress> controllerNodeIPLookupMap =
                new HashMap<String, DualInetAddress>();
        controllerNodeIPLookupMap.put("localhost", localAddress);
        map.setControllerNodeIPLookupMap(controllerNodeIPLookupMap);
        coordinatorClient.setInetAddessLookupMap(map);

        coordinatorClient.start();

        FileInputStream is = new FileInputStream(defaultOvfPropsLocation);
        Properties defaultProp = new Properties();
        defaultProp.load(is);
        is.close();
        is = new FileInputStream(ovfPropsLocation);
        Properties ovfProps = new Properties();
        ovfProps.load(is);
        is.close();

        CoordinatorClientImpl.setDefaultProperties(defaultProp);
        CoordinatorClientImpl.setOvfProperties(ovfProps);

        ks = KeyStoreUtil.getViPRKeystore(coordinatorClient);
        KeyCertificateAlgorithmValuesHolder values =
                new KeyCertificateAlgorithmValuesHolder(coordinatorClient);
        gen = new KeyCertificatePairGenerator();
        gen.setKeyCertificateAlgorithmValuesHolder(values);
        entry = gen.generateKeyCertificatePair();

        hostName = System.getenv(KeyCertificatePairGeneratorTest.LOCALHOST_IP);
        if (StringUtils.isBlank(hostName)) {
            hostName = "localhost";
        }
        webServer = new TestWebServer(entry);
        webServer.start();
    }

    @Test
    public void testSSLSocketFactory() throws Exception {
        ViPRSSLSocketFactory sslSocketFactory =
                new ViPRSSLSocketFactory(coordinatorClient);

        // set the setting to accept all certs to true and try to connect - should
        // succeed
        KeyStoreUtil.setAcceptAllCertificates(coordConfigStoringHelper, Boolean.TRUE);

        URL url = new URL("https", hostName, TestWebServer._securePort, "/test");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(sslSocketFactory);
        connection.setRequestMethod("GET");
        InputStream inputStream = connection.getInputStream();
        byte[] result = new byte[1];
        int res = inputStream.read(result);
        Assert.assertEquals(1, res);
        Assert.assertEquals("1", new String(result));
        connection.disconnect();

        // set the setting to accept all certs to false and try again to connect - should
        // fail
        KeyStoreUtil.setAcceptAllCertificates(coordConfigStoringHelper, Boolean.FALSE);
        // recreating the sslsocketfactory since a reboot is triggered after changes to
        // the truststore
        sslSocketFactory = new ViPRSSLSocketFactory(coordinatorClient);

        connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(sslSocketFactory);
        connection.setRequestMethod("GET");
        boolean exceptionTrhown = false;
        try {
            inputStream = connection.getInputStream();
        } catch (SSLHandshakeException e) {
            exceptionTrhown = true;
        }
        Assert.assertEquals(true, exceptionTrhown);
        connection.disconnect();

        // add the certificate to the keystore and try to connect - should succeed
        ks.setCertificateEntry("some_alias", entry.getCertificateChain()[0]);
        // recreating the sslsocketfactory since a reboot is triggered after changes to
        // the truststore
        sslSocketFactory = new ViPRSSLSocketFactory(coordinatorClient);
        connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(sslSocketFactory);
        connection.setRequestMethod("GET");
        inputStream = connection.getInputStream();
        result = new byte[1];
        res = inputStream.read(result);
        Assert.assertEquals(1, res);
        Assert.assertEquals("2", new String(result));
        connection.disconnect();
    }

    @After
    public void destroy() throws Exception {
        webServer.stop();
    }
}
