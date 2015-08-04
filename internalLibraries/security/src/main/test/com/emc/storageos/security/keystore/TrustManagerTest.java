/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.emc.storageos.security.ApplicationContextUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.keystore.impl.DistributedKeyStoreImpl;
import com.emc.storageos.security.keystore.impl.DistributedLoadKeyStoreParam;
import com.emc.storageos.security.keystore.impl.KeyCertificateAlgorithmValuesHolder;
import com.emc.storageos.security.keystore.impl.KeyCertificateEntry;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.keystore.impl.TrustedCertificateEntry;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;

/**
 * 
 */
public class TrustManagerTest {

    private final String server = "localhost";
    private final String coordinatorServer = "coordinator://" + server + ":2181";
    private final String defaultOvfPropsLocation = "/etc/config.defaults";
    private final String ovfPropsLocation = "/etc/ovfenv.properties";
    private final CoordinatorClientImpl coordinatorClient = new CoordinatorClientImpl();
    private final CoordinatorConfigStoringHelper zkhHelper = new CoordinatorConfigStoringHelper(coordinatorClient);
    private DistributedLoadKeyStoreParam loadStoreParam;

    @Before
    public void setup() throws IOException, URISyntaxException {
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

        loadStoreParam = new DistributedLoadKeyStoreParam();
        loadStoreParam.setCoordinator(coordinatorClient);
    }

    @Test
    public void testCheckServerTrusted() throws Exception {

        DistributedKeyStore zookeeperKeystore = new DistributedKeyStoreImpl();
        zookeeperKeystore.init(loadStoreParam);
        zookeeperKeystore.setTrustedCertificates(null);
        KeyStoreUtil.setAcceptAllCertificates(zkhHelper, Boolean.FALSE);

        ViPRX509TrustManager tm = new ViPRX509TrustManager(coordinatorClient);
        KeyCertificatePairGenerator gen = new KeyCertificatePairGenerator();
        gen.setKeyCertificateAlgorithmValuesHolder(new KeyCertificateAlgorithmValuesHolder(
                coordinatorClient));

        KeyCertificateEntry entry = gen.generateKeyCertificatePair();

        X509Certificate[] chainToVerify =
                new X509Certificate[] { (X509Certificate) entry.getCertificateChain()[0] };
        boolean exceptionThrown = false;
        try {
            tm.checkServerTrusted(chainToVerify, "RSA_EXPORT");
        } catch (CertificateException e) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        TrustedCertificateEntry trustedCert =
                new TrustedCertificateEntry(entry.getCertificateChain()[0], new Date());
        zookeeperKeystore.addTrustedCertificate("someAlias", trustedCert);
        // creating a new instance since trust manager caches all the certs
        tm = new ViPRX509TrustManager(coordinatorClient);

        try {
            tm.checkServerTrusted(chainToVerify, "RSA_EXPORT");
        } catch (CertificateException e) {
            Assert.fail();
        }

        KeyStoreUtil.setAcceptAllCertificates(zkhHelper, Boolean.TRUE);
        entry = gen.generateKeyCertificatePair();
        chainToVerify =
                new X509Certificate[] { (X509Certificate) entry.getCertificateChain()[0] };
        try {
            tm.checkServerTrusted(chainToVerify, "RSA_EXPORT");
        } catch (CertificateException e) {
            Assert.fail();
        }
    }
}
