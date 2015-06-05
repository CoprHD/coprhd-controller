/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.keystore;

import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.SSLHandshakeException;

import com.emc.storageos.security.ApplicationContextUtil;
import org.junit.Assert;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
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
import com.emc.storageos.security.ssl.ViPRProtocolSocketFactory;

/**
 * 
 */
public class ProtocolSocketFactoryTest {

    private final String coordinatorServer = "coordinator://localhost:2181";
    private final String defaultOvfPropsLocation = "/etc/config.defaults";
    private final String ovfPropsLocation = "/etc/ovfenv.properties";
    private final CoordinatorClientImpl coordinatorClient = new CoordinatorClientImpl();
    private final CoordinatorConfigStoringHelper coordConfigStoringHelper = new CoordinatorConfigStoringHelper(coordinatorClient);

    private KeyStore ks;
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
        KeyCertificatePairGenerator gen = new KeyCertificatePairGenerator();
        gen.setKeyCertificateAlgorithmValuesHolder(new KeyCertificateAlgorithmValuesHolder(
                coordinatorClient));
        entry = gen.generateKeyCertificatePair();

        hostName = System.getenv(KeyCertificatePairGeneratorTest.LOCALHOST_IP);
        if (StringUtils.isBlank(hostName)) {
            hostName = "localhost";
        }
        webServer = new TestWebServer(entry);
        webServer.start();
    }

    @Test
    public void testProtocolSocketFactory() throws Exception {

        // set the setting to accept all certs to true and try to connect - should
        // succeed
        KeyStoreUtil.setAcceptAllCertificates(coordConfigStoringHelper, Boolean.TRUE);
        Protocol protocol =
                new Protocol("https", new ViPRProtocolSocketFactory(coordinatorClient),
                        9930);
        HttpClient client = new HttpClient();
        client.getHostConfiguration().setHost(hostName, 9930, protocol);
        HttpMethod method = new GetMethod("/test");
        client.executeMethod(method);
        String result = method.getResponseBodyAsString();
        Assert.assertEquals("1", result);
        method.releaseConnection();

        // set the setting to accept all certs to false and try again to connect - should
        // fail
        KeyStoreUtil.setAcceptAllCertificates(coordConfigStoringHelper, Boolean.FALSE);
        // recreating the protocolSocketFactory since a reboot is triggered after changes
        // to the truststore
        protocol =
                new Protocol("https", new ViPRProtocolSocketFactory(coordinatorClient),
                        9930);
        client = new HttpClient();
        client.getHostConfiguration().setHost(hostName, 9930, protocol);
        method = new GetMethod("/test");
        boolean exceptionThrown = false;
        try {
            client.executeMethod(method);
        } catch (SSLHandshakeException e) {
            exceptionThrown = true;
        }
        Assert.assertEquals(true, exceptionThrown);
        method.releaseConnection();

        // add the certificate to the keystore and try to connect - should succeed
        ks.setCertificateEntry("some_alias", entry.getCertificateChain()[0]);
        // recreating the protocolSocketFactory since a reboot is triggered after changes
        // to the truststore
        protocol =
                new Protocol("https", new ViPRProtocolSocketFactory(coordinatorClient),
                        9930);
        client = new HttpClient();
        client.getHostConfiguration().setHost(hostName, 9930, protocol);
        method = new GetMethod("/test");
        client.executeMethod(method);
        result = method.getResponseBodyAsString();
        Assert.assertEquals("2", result);
        method.releaseConnection();
    }

    @After
    public void destroy() throws Exception {
        webServer.stop();
    }

}
