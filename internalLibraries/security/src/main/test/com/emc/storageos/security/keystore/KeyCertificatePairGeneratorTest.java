/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013-2014 EMC Corporation All Rights Reserved
 * 
 * This software contains the intellectual property of EMC Corporation or is licensed to
 * EMC Corporation from third parties. Use of this software and the intellectual property
 * contained therein is expressly limited to the terms and conditions of the License
 * Agreement under which it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.keystore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.*;

import com.emc.storageos.security.ApplicationContextUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.keystore.impl.KeyCertificateAlgorithmValuesHolder;
import com.emc.storageos.security.keystore.impl.KeyCertificateEntry;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

/**
 * Test class for the key generator NOTE: I wanted to try with an algorithm other than
 * RSA, but there is no other asymmetric algorithm, supported by default in Java 6. This
 * is not the case Java 7, or if we decide to add BouncyCastle to our code.
 */
public class KeyCertificatePairGeneratorTest {

    static final String LOCALHOST_IP = "LOCALHOST_IP";
    private static final String CERTIFICATE_CRT_PATH =
            System.getProperty("user.dir")
                    + "/src/main/test/com/emc/storageos/security/keystore/RSACorporateServerCAv2.crt";
    private static final String CERTIFICATE_PEM_PATH =
            System.getProperty("user.dir")
                    + "/src/main/test/com/emc/storageos/security/keystore/RSACorporateServerCAv2.pem";
    private static final String CERTIFICATE_DER_PATH =
            System.getProperty("user.dir")
                    + "/src/main/test/com/emc/storageos/security/keystore/RSACorporateServerCAv2.der";
    private static final String CERTIFICATE_P7B_PATH =
            System.getProperty("user.dir")
                    + "/src/main/test/com/emc/storageos/security/keystore/RSACorporateServerCAv2.p7b";
    private static final String CERTIFICATE_P7C_PATH =
            System.getProperty("user.dir")
                    + "/src/main/test/com/emc/storageos/security/keystore/RSACorporateServerCAv2.p7c";
    private static final String CERTIFICATE_SHA_FINGERPRINT =
            "d67b3f1a57ec78fb2b9c1c3a8acdf789406dcc5a";
    private static final BigInteger CERTIFICATE_SERIAL_NUMBER = new BigInteger(
            "9f6c8bd34b73594063e476d369fdfdfa", 16);
    private static final BigInteger KEY_MODULUS =
            new BigInteger(
                    "141415449813198352640584459030316068987610408486312003311918794919743922191781906413424250578755834293932792374821645795933424858325487735796194380710154232701027417945794983447064558015683473053823226311432078689729475996612733400159900353688889555372959862723633150803411582349992187423815121258678374346333");
    private static final BigInteger KEY_PUBLIC_EXPONENT = new BigInteger("65537");
    private static final BigInteger KEY_PRIVATE_EXPONENT =
            new BigInteger(
                    "75827094952249924624910790466215069049447142295458902732565396223121613283205028812337177313396383309017688585739467093624921539686222508256619417009254757657631145102770592566649719745911311814270927270971024131074790025383305539659913846230309109245669666976136262822378774211143821358923578648285938595297");
    private static final BigInteger KEY_PRIME_P =
            new BigInteger(
                    "12935208964662720932996951651139035181084113865514099158277613600888578618673901494975661202391906427707617503913003448716646973088555538399634321073962149");
    private static final BigInteger KEY_PRIME_Q =
            new BigInteger(
                    "10932598785185971625219171195400373011175996212037831824584500027127226918142419249584498881264583931973989625943739172400847843303631949509141140671440217");
    private static final BigInteger KEY_PRIME_EXPONENT_P =
            new BigInteger(
                    "10701543098768828737767897806197392122287877897800852287437816949817335744762484383746286073419429734505806202025772418472261453543211945802038129432690353");
    private static final BigInteger KEY_PRIME_EXPONENT_Q =
            new BigInteger(
                    "4531214135557113497044850194251496591277806202718824875279441754075665117071311382523822315818391036272784537124216658375028304128302403901869185407606857");
    private static final BigInteger KEY_CRT_COEFFICIENT =
            new BigInteger(
                    "4545576255440851624075975079297381702161407904537264067567341406895999109859305066801694272317323799472024724727092257412713562533441587060260584920416954");

    private static final String RSA_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\r\n"
            + "MIICXAIBAAKBgQDJYd1wxB87o/tq+1MZdIzveINYXjPDUmgjT5ZCZlZjjV2HdG1w\r\n"
            + "h9Pp6Y6cav45c7BcvQq4fsmT5pZVzOG5U5cJTEdjPJ9nOJrh11gQJVenOXqGRI73\r\n"
            + "pZzhiG4uWNFV84skxU4ObPTsernN8kTrNQoygR8wTsZziwAaY2zhvyuuXQIDAQAB\r\n"
            + "AoGAa/s65s1yxeMO2/V5QIv7Sii/nPGeJdyZFF4HfwEqz2SswwYN7KoYWjOvEXZZ\r\n"
            + "bOr4pTGEfxsU8WZSNB2Q53PH5vMQ1B1/72QZUnLoKLOGR/EOX5RA1PhM6Ea5u18P\r\n"
            + "wzJ9TcvHsH1QIxEH0pep2qhWIl8D6JjdYaIliPynCGOONeECQQD2+fwAIUPZa5Wg\r\n"
            + "s3t/z6azFOlKdnITdkqm1B0erwl9ZgN5ZiS9P5BS30PNXxJ0EiZdXJzPcBIZFuY2\r\n"
            + "NJZAsvylAkEA0L1uAkdGroFYDp1xu3HJcO0JGkrO3Rs6W6G697damYgYB4pfTTng\r\n"
            + "pUd9FgLu4HLb6Y0muGpB8sXirb2aB1YlWQJBAMxUDZTd8JBUXbpSQ35+gV/vkQK1\r\n"
            + "87L+Tsyu+FiGX8eLOpyZURPxHqoxZJroaQ/2ZB8hm+pSweZX96Yo45YrfrECQFaE\r\n"
            + "HQNuvVn4nCG6mfgB6mcWp64xEVpNPbva5Z5kbXWzFZqSfHuKoJSAc9TatF1s3b8I\r\n"
            + "VOMcj2brI8+1BRFDYEkCQFbKUFyfFOjVZczK2NdbXSrO3iSTIZFOGETa9dxrchRP\r\n"
            + "/TXoqhkTuzu9y/E8QVQXBdCEXD72v5sn1kl1hd8Pgro=\r\n"
            + "-----END RSA PRIVATE KEY-----";

    private KeyCertificateAlgorithmValuesHolder defaultValues;
    private String localhostIP = "localhost";
    private String localhostName = null;

    private final String server = "localhost";
    private final String coordinatorServer = "coordinator://" + server + ":2181";
    private final String defaultOvfPropsLocation = "/etc/config.defaults";
    private final String ovfPropsLocation = "/etc/ovfenv.properties";
    private final CoordinatorClientImpl coordinatorClient = new CoordinatorClientImpl();

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

        defaultValues = new KeyCertificateAlgorithmValuesHolder(coordinatorClient);

        String envVar = System.getenv(LOCALHOST_IP);
        if (StringUtils.isNotBlank(envVar)) {
            localhostIP = envVar;
        }
        InetAddress localhost = InetAddress.getByName(localhostIP);
        localhostName = localhost.getCanonicalHostName();
    }

    @Test
    public void testGenerate() throws GeneralSecurityException, IOException {
        // test the defaults
        KeyCertificatePairGenerator gen = new KeyCertificatePairGenerator();
        gen.setKeyCertificateAlgorithmValuesHolder(defaultValues);
        KeyCertificateEntry pair = gen.generateKeyCertificatePair();
        AssertCertInformation((X509Certificate) pair.getCertificateChain()[0],
                defaultValues);
    }

    @Test
    public void testLoadKey() throws SecurityException, NoSuchAlgorithmException {
        // test the defaults
        KeyCertificatePairGenerator rsaGen = new KeyCertificatePairGenerator();
        rsaGen.setKeyCertificateAlgorithmValuesHolder(defaultValues);
        KeyCertificateEntry pair = rsaGen.generateKeyCertificatePair();
        byte[] RSAKeyBytes = pair.getKey();
        PrivateKey loadedRSAKey =
                KeyCertificatePairGenerator.loadPrivateKeyFromBytes(RSAKeyBytes);
        byte[] loadedRSAKeyBytes = loadedRSAKey.getEncoded();
        Assert.assertEquals(RSAKeyBytes.length, loadedRSAKeyBytes.length);
        Assert.assertArrayEquals(RSAKeyBytes, loadedRSAKeyBytes);
    }

    @Test
    public void testVerifyKeyCertificateEntry() {
        KeyCertificatePairGenerator gen = new KeyCertificatePairGenerator();
        gen.setKeyCertificateAlgorithmValuesHolder(defaultValues);

        // test a generated entry
        KeyCertificateEntry entry1 = gen.generateKeyCertificatePair();
        try {
            new KeyCertificatePairGenerator().verifyKeyCertificateEntry(entry1);
        } catch (SecurityException e) {
            System.err.println(e.getMessage());
            System.err.println(e);
            Assert.fail();
        } catch (BadRequestException e) {
            System.err.println(e.getMessage());
            System.err.println(e);
            Assert.fail();
        }

        // test values from 2 different generated entries
        KeyCertificateEntry entry2 = gen.generateKeyCertificatePair();
        KeyCertificateEntry hybridEntry =
                new KeyCertificateEntry(entry1.getKey(), entry2.getCertificateChain());
        boolean exceptionThrown = false;
        try {
            new KeyCertificatePairGenerator().verifyKeyCertificateEntry(hybridEntry);
        } catch (SecurityException e) {
            Assert.fail();
        } catch (BadRequestException e) {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

    @Test
    public void testGetCertificateChainFromString() throws IOException, CertificateException {
        String crtCertificate = getStringFromFile(CERTIFICATE_CRT_PATH);
        String derCertificate = getStringFromFile(CERTIFICATE_DER_PATH);
        String p7bCertificate = getStringFromFile(CERTIFICATE_P7B_PATH);
        String p7cCertificate = getStringFromFile(CERTIFICATE_P7C_PATH);
        String pemCertificate = getStringFromFile(CERTIFICATE_PEM_PATH);

        Certificate[] pemCertChain =
                KeyCertificatePairGenerator.getCertificateChainFromString(pemCertificate);
        Assert.assertEquals(3, pemCertChain.length);
        X509Certificate cert = (X509Certificate) pemCertChain[0];
        Assert.assertEquals(CERTIFICATE_SERIAL_NUMBER, cert.getSerialNumber());
        Assert.assertEquals(CERTIFICATE_SHA_FINGERPRINT,
                DigestUtils.shaHex(cert.getEncoded()));

        Certificate[] certChain =
                KeyCertificatePairGenerator.getCertificateChainFromString(crtCertificate);
        Assert.assertArrayEquals(pemCertChain, certChain);

        boolean exceptionThrown = false;
        try {
            certChain =
                    KeyCertificatePairGenerator.getCertificateChainFromString(derCertificate);
            Assert.assertArrayEquals(pemCertChain, certChain);
        } catch (CertificateException e) {
            // we won't support DER encoded certificate chains with more than 1
            // certificate
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            certChain =
                    KeyCertificatePairGenerator
                            .getCertificateChainFromString(p7bCertificate);
            Assert.fail();
        } catch (CertificateException e) {
            // we won't support p7c encoded certificate chains with more than 1
            // certificate
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            certChain =
                    KeyCertificatePairGenerator
                            .getCertificateChainFromString(p7cCertificate);
            Assert.fail();
        } catch (CertificateException e) {
            // we won't support p7c encoded certificate chains with more than 1
            // certificate
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

    /**
     * @throws FileNotFoundException
     * @throws IOException
     */
    private String getStringFromFile(String filePath) throws FileNotFoundException,
            IOException {
        File file;
        FileInputStream fis;
        byte[] data;
        file = new File(filePath);
        fis = new FileInputStream(file);
        data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return new String(data);
    }

    @Test
    public void testGetCertificateChainAsString() throws IOException,
            CertificateException {
        String pemCertificate = getStringFromFile(CERTIFICATE_PEM_PATH);
        Certificate[] pemCertChain =
                KeyCertificatePairGenerator.getCertificateChainFromString(pemCertificate);
        String generatedPemCertificate =
                KeyCertificatePairGenerator.getCertificateChainAsString(pemCertChain);
        Certificate[] generatedPemCertChain =
                KeyCertificatePairGenerator
                        .getCertificateChainFromString(generatedPemCertificate);

        Assert.assertArrayEquals(pemCertChain, generatedPemCertChain);
    }

    /*
     * @Test
     * public void testGetKeyBytesFromString() throws FileNotFoundException,
     * SecurityException, NoSuchAlgorithmException, Exception {
     * byte[] keyBytes =
     * KeyCertificatePairGenerator.loadPrivateKeyFromPEMString(RSA_PRIVATE_KEY);
     * RSAPrivateCrtKey rsaKey =
     * (RSAPrivateCrtKey) KeyCertificatePairGenerator
     * .loadPrivateKeyFromBytes(keyBytes);
     * Assert.assertEquals(KEY_MODULUS, rsaKey.getModulus());
     * Assert.assertEquals(KEY_PUBLIC_EXPONENT, rsaKey.getPublicExponent());
     * Assert.assertEquals(KEY_PRIVATE_EXPONENT, rsaKey.getPrivateExponent());
     * Assert.assertEquals(KEY_PRIME_P, rsaKey.getPrimeP());
     * Assert.assertEquals(KEY_PRIME_Q, rsaKey.getPrimeQ());
     * Assert.assertEquals(KEY_PRIME_EXPONENT_P, rsaKey.getPrimeExponentP());
     * Assert.assertEquals(KEY_PRIME_EXPONENT_Q, rsaKey.getPrimeExponentQ());
     * Assert.assertEquals(KEY_CRT_COEFFICIENT, rsaKey.getCrtCoefficient());
     * }
     */
    /**
     * @param x509Certificate
     */
    private void AssertCertInformation(X509Certificate x509Certificate, KeyCertificateAlgorithmValuesHolder valuesHolder) {
        try {
            x509Certificate.checkValidity();
        } catch (CertificateExpiredException e) {
            Assert.fail("The certificate that was created is expired. Details: "
                    + e.getMessage());
        } catch (CertificateNotYetValidException e) {
            Assert.fail("The certificate that was created is not yet valid. Details: "
                    + e.getMessage());
        }

        // NOTBEFORE should be sometime in past say at least 13 days ago.
        checkNotBefore(x509Certificate.getNotBefore());

        Assert.assertEquals(x509Certificate.getNotAfter().getTime() - x509Certificate.getNotBefore().getTime(),
                valuesHolder.getCertificateValidityInDays() * 86400000l);
        String issuerDN = null;
        if (StringUtils.isNotBlank(localhostName)) {
            issuerDN =
                    String.format(KeyCertificatePairGenerator.CERTIFICATE_COMMON_NAME_FORMAT,
                            localhostName);
        } else {
            issuerDN =
                    String.format(KeyCertificatePairGenerator.CERTIFICATE_COMMON_NAME_FORMAT, localhostIP);
        }
        Assert.assertEquals(x509Certificate.getSubjectDN().getName(), issuerDN);
        Assert.assertEquals(x509Certificate.getIssuerDN().getName(), issuerDN);
        Assert.assertEquals(x509Certificate.getPublicKey().getAlgorithm(),
                KeyCertificateAlgorithmValuesHolder.DEFAULT_KEY_ALGORITHM);
        Assert.assertEquals(x509Certificate.getVersion(), 3);
        Assert.assertEquals(x509Certificate.getSigAlgName(), valuesHolder.getSigningAlgorithm());
    }

    private void checkNotBefore(Date notBefore) {
        long diff = new Date().getTime() - notBefore.getTime();
        long daysDiff = diff / 24 / 3600 / 1000;
        if (diff < 13) {
            Assert.fail("The diff of NotBefore from today should be > 13 days but now it is " + daysDiff);
        }
    }

}
