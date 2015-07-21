/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.KeyStore.LoadStoreParameter;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import com.emc.storageos.security.ApplicationContextUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.keystore.impl.DistributedKeyStoreImpl;
import com.emc.storageos.security.keystore.impl.DistributedLoadKeyStoreParam;
import com.emc.storageos.security.keystore.impl.KeyCertificateAlgorithmValuesHolder;
import com.emc.storageos.security.keystore.impl.KeyCertificateEntry;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import com.emc.storageos.security.keystore.impl.SecurityProvider;
import com.emc.storageos.security.keystore.impl.TrustedCertificateEntry;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * 
 */
public class KeystoreTest {

    private final String coordinatorServer = "coordinator://localhost:2181";
    private final String defaultOvfPropsLocation = "/etc/config.defaults";
    private final String ovfPropsLocation = "/etc/ovfenv.properties";
    private DistributedLoadKeyStoreParam loadStoreParam;
    private LoadStoreParameter invalidLoadStoreParam;
    private KeyCertificatePairGenerator gen;
    private final CoordinatorClientImpl coordinatorClient = new CoordinatorClientImpl();

    @Before
    public void setup() throws URISyntaxException, IOException {
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
        invalidLoadStoreParam = new LoadStoreParameter() {

            @Override
            public ProtectionParameter getProtectionParameter() {
                return null;
            }
        };
        gen = new KeyCertificatePairGenerator();
        KeyCertificateAlgorithmValuesHolder values =
                new KeyCertificateAlgorithmValuesHolder(coordinatorClient);
        gen.setKeyCertificateAlgorithmValuesHolder(values);
    }


    @Test
    public void testZookeeperKeystore() throws IOException {
        DistributedKeyStore zookeeperKeystore = new DistributedKeyStoreImpl();
        boolean exceptionThrown = false;
        try {
            zookeeperKeystore.init(invalidLoadStoreParam);
        } catch (SecurityException e) {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);

        zookeeperKeystore.init(loadStoreParam);

        // this is in case this test was run previously
        zookeeperKeystore.setTrustedCertificates(null);

        KeyCertificateEntry origEntry = gen.generateKeyCertificatePair();
        origEntry.setCreationDate(new Date());
        zookeeperKeystore.setKeyCertificatePair(origEntry);
        KeyCertificateEntry storedEntry = zookeeperKeystore.getKeyCertificatePair();
        assertKeyCertificateEntriesEquals(origEntry, storedEntry);

        origEntry = gen.generateKeyCertificatePair();
        TrustedCertificateEntry origCertEntry =
                new TrustedCertificateEntry(origEntry.getCertificateChain()[0],
                        new Date());
        Map<String, TrustedCertificateEntry> origCertEntries =
                new HashMap<String, TrustedCertificateEntry>();
        origCertEntries.put("trustedCert1", origCertEntry);
        zookeeperKeystore.addTrustedCertificate("trustedCert1", origCertEntry);

        origEntry = gen.generateKeyCertificatePair();
        origCertEntry =
                new TrustedCertificateEntry(origEntry.getCertificateChain()[0],
                        new Date());
        origCertEntries.put("trustedCert2", origCertEntry);
        zookeeperKeystore.addTrustedCertificate("trustedCert2", origCertEntry);

        assertTrustedCertsEquals(origCertEntries,
                zookeeperKeystore.getTrustedCertificates());

        origEntry = gen.generateKeyCertificatePair();
        origCertEntry =
                new TrustedCertificateEntry(origEntry.getCertificateChain()[0],
                        new Date());
        origCertEntries.put("trustedCert3", origCertEntry);
        zookeeperKeystore.setTrustedCertificates(origCertEntries);

        assertTrustedCertsEquals(origCertEntries,
                zookeeperKeystore.getTrustedCertificates());

        origCertEntries.remove("trustedCert3");
        zookeeperKeystore.setTrustedCertificates(origCertEntries);

        assertTrustedCertsEquals(origCertEntries,
                zookeeperKeystore.getTrustedCertificates());

        origCertEntries.remove("trustedCert2");
        zookeeperKeystore.removeTrustedCertificate("trustedCert2");
        assertTrustedCertsEquals(origCertEntries,
                zookeeperKeystore.getTrustedCertificates());

        zookeeperKeystore.removeTrustedCertificate("trustedCert10");

    }

    /**
     * @param expected
     * @param actual
     */
    private void assertTrustedCertsEquals(
            Map<String, TrustedCertificateEntry> expected,
            Map<String, TrustedCertificateEntry> actual) {
        if (CollectionUtils.isEmpty(expected)) {
            Assert.assertTrue(CollectionUtils.isEmpty(actual));
        } else {
            Assert.assertFalse(CollectionUtils.isEmpty(actual));
            Assert.assertEquals(expected.size(), actual.size());
            for (Entry<String, TrustedCertificateEntry> entry : expected.entrySet()) {
                Assert.assertTrue(actual.containsKey(entry.getKey()));
                assertEqualCertificateEntry(entry.getValue(), actual.get(entry.getKey()));
            }
        }
    }

    /**
     * @param value
     * @param trustedCertificateEntry
     */
    private void assertEqualCertificateEntry(TrustedCertificateEntry expected,
            TrustedCertificateEntry actual) {
        Assert.assertEquals(expected.getCreationDate(), actual.getCreationDate());
        Assert.assertEquals(expected.getCertificate(), actual.getCertificate());
    }

    /**
     * @param expected
     * @param actual
     */
    private void assertKeyCertificateEntriesEquals(KeyCertificateEntry expected,
            KeyCertificateEntry actual) {
        org.junit.Assert.assertArrayEquals(expected.getKey(), actual.getKey());
        Assert.assertEquals(expected.getCertificateChain()[0],
                actual.getCertificateChain()[0]);
        Assert.assertEquals(expected.getCreationDate(), actual.getCreationDate());
    }


    @Test
    public void testKeystoreEngine() throws KeyStoreException, NoSuchAlgorithmException,
    CertificateException, IOException, InterruptedException,
    UnrecoverableEntryException {
        DistributedKeyStore zookeeperKeystore = new DistributedKeyStoreImpl();
        zookeeperKeystore.init(loadStoreParam);

        // this is in case this test was run previously
        zookeeperKeystore.setTrustedCertificates(null);
        zookeeperKeystore.setKeyCertificatePair(null);

        // test keystore loading
        KeyStore ks =
                KeyStore.getInstance(SecurityProvider.KEYSTORE_TYPE,
                        new SecurityProvider());
        boolean exceptionThrown = false;
        try {
            ks.load(null, null);
        } catch (SecurityException e) {
            Assert.assertEquals(ServiceCode.SECURITY_ERROR, e.getServiceCode());
            Assert.assertEquals(
                    "Could not initialize the keystore. The ViPR keystore can only be initialized with a LoadKeyStoreParam.",
                    e.getMessage());
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            ks.load(invalidLoadStoreParam);
        } catch (SecurityException e) {
            Assert.assertEquals(ServiceCode.SECURITY_ERROR, e.getServiceCode());
            Assert.assertEquals(
                    "Could not initialize the keystore. The ViPR keystore can only be initialized with a LoadKeyStoreParam.",
                    e.getMessage());
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);

        // now it shouldn't throw
        ks.load(loadStoreParam);

        //////////////////////////////////////////////////////////////////////////
        ///
        /// key tests
        ///
        //////////////////////////////////////////////////////////////////////////

        //should have by default the ViPR key
        List<String> expectedAliases = new ArrayList<String>();
        expectedAliases.add(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
        assertAliasesIn(ks, expectedAliases);

        // update the vipr key using ks.setEntry
        Date beforeDate = new Date();
        KeyCertificateEntry entry = gen.generateKeyCertificatePair();
        KeyStore.PrivateKeyEntry privateKeyEntry =
                new PrivateKeyEntry(
                        KeyCertificatePairGenerator.loadPrivateKeyFromBytes(entry
                                .getKey()),
                                entry.getCertificateChain());
        KeyStore.PasswordProtection empryProtectionParam =
                new KeyStore.PasswordProtection("".toCharArray());

        ks.setEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, privateKeyEntry,
                new KeyStore.PasswordProtection("123".toCharArray()));
        Date afterDate = new Date();

        assertKeyCertificateEntryEquals(ks, entry);
        assertCreationDateInTImeRange(ks, KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                beforeDate, afterDate);

        // set the key entry using setKeyEntry (there are 2 versions, one with the Key
        // object, and another with byte[] )
        beforeDate = new Date();
        entry = gen.generateKeyCertificatePair();
        ks.setKeyEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, entry.getKey(),
                entry.getCertificateChain());
        afterDate = new Date();
        assertKeyCertificateEntryEquals(ks, entry);
        assertCreationDateInTImeRange(ks, KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                beforeDate, afterDate);

        beforeDate = new Date();
        entry = gen.generateKeyCertificatePair();
        ks.setKeyEntry(
                KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                KeyCertificatePairGenerator.loadPrivateKeyFromBytes(entry.getKey()),
                "".toCharArray(),
                entry.getCertificateChain());
        afterDate = new Date();
        assertKeyCertificateEntryEquals(ks, entry);
        assertCreationDateInTImeRange(ks, KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                beforeDate, afterDate);

        //////////////////////////////////////////////////////////////////////////
        ///
        /// certificates tests
        ///
        //////////////////////////////////////////////////////////////////////////
        String certAlias = "someCert";
        // add a new trusted certificate using ks.setEntry
        beforeDate = new Date();
        entry = gen.generateKeyCertificatePair();
        KeyStore.TrustedCertificateEntry trustedCertEntry =
                new KeyStore.TrustedCertificateEntry(entry.getCertificateChain()[0]);
        ks.setEntry(certAlias, trustedCertEntry, null);
        afterDate = new Date();
        expectedAliases.add(certAlias);
        assertAliasesIn(ks, expectedAliases);
        assertTrustedCertEquals(ks, entry, certAlias);
        assertCreationDateInTImeRange(ks, certAlias, beforeDate, afterDate);

        // add a new trusted certificate using ks.setCertificateEntry
        beforeDate = new Date();
        entry = gen.generateKeyCertificatePair();
        certAlias = "someCert1";
        ks.setCertificateEntry(certAlias, entry.getCertificateChain()[0]);
        afterDate = new Date();
        expectedAliases.add(certAlias);
        assertAliasesIn(ks, expectedAliases);
        assertTrustedCertEquals(ks, entry, certAlias);
        assertCreationDateInTImeRange(ks, certAlias, beforeDate, afterDate);

        // remove the trusted certificate entry
        ks.deleteEntry(certAlias);
        expectedAliases.remove(certAlias);
        assertAliasesIn(ks, expectedAliases);

        //////////////////////////////////////////////////////////////////////////
        ///
        /// Negative testing
        ///
        //////////////////////////////////////////////////////////////////////////

        String invalidEntryName = "invalidEntry";
        // cannot delete the ViPR key
        exceptionThrown = false;
        try {
            ks.deleteEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
        } catch (SecurityException e) {
            Assert.assertEquals(ServiceCode.SECURITY_ERROR, e.getServiceCode());
            Assert.assertEquals(
                    "The ViPR key and certificate cannot be deleted, it can only be updated.",
                    e.getMessage());
            exceptionThrown = true;
        } catch (KeyStoreException e) {
            Assert.fail();
        }
        Assert.assertTrue(exceptionThrown);
        assertAliasesIn(ks, expectedAliases);

        entry = gen.generateKeyCertificatePair();
        // try to set a key that is not the vipr key
        //using  ks.setEntry
        privateKeyEntry =
                new PrivateKeyEntry(
                        KeyCertificatePairGenerator.loadPrivateKeyFromBytes(entry
                                .getKey()), entry.getCertificateChain());
        exceptionThrown = false;

        try {
            ks.setEntry(invalidEntryName, privateKeyEntry, empryProtectionParam);
        } catch (SecurityException e) {
            Assert.assertEquals(ServiceCode.SECURITY_ERROR, e.getServiceCode());
            Assert.assertEquals(
                    "Cannot update any key and certificate entry except for the ViPR key and certificate.",
                    e.getMessage());
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
        assertAliasesIn(ks, expectedAliases);

        //using  ks.setKey which accepts byte[]
        try {
            ks.setKeyEntry(invalidEntryName, entry.getKey(), entry.getCertificateChain());
        } catch (SecurityException e) {
            Assert.assertEquals(ServiceCode.SECURITY_ERROR, e.getServiceCode());
            Assert.assertEquals(
                    "Cannot update any key and certificate entry except for the ViPR key and certificate.",
                    e.getMessage());
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
        assertAliasesIn(ks, expectedAliases);

        //using  ks.setKey which accepts Key object
        try {
            ks.setKeyEntry(invalidEntryName,
                    KeyCertificatePairGenerator.loadPrivateKeyFromBytes(entry.getKey()),
                    "".toCharArray(), entry.getCertificateChain());
        } catch (SecurityException e) {
            Assert.assertEquals(ServiceCode.SECURITY_ERROR, e.getServiceCode());
            Assert.assertEquals(
                    "Cannot update any key and certificate entry except for the ViPR key and certificate.",
                    e.getMessage());
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
        assertAliasesIn(ks, expectedAliases);

        //try getting an invalid entry
        Assert.assertFalse(ks.containsAlias(invalidEntryName));
        Assert.assertFalse(ks.entryInstanceOf(invalidEntryName, KeyStore.TrustedCertificateEntry.class));
        Assert.assertFalse(ks.entryInstanceOf(invalidEntryName, KeyStore.PrivateKeyEntry.class));
        Assert.assertFalse(ks.entryInstanceOf(invalidEntryName, KeyStore.SecretKeyEntry.class));
        Assert.assertFalse(ks.isCertificateEntry(invalidEntryName));
        Assert.assertFalse(ks.isKeyEntry(invalidEntryName));

        Assert.assertNull(ks.getCertificate(invalidEntryName));
        Assert.assertNull(ks.getCertificateAlias(entry.getCertificateChain()[0]));
        Assert.assertNull(ks.getCertificateChain(invalidEntryName));
        Assert.assertNull(ks.getCreationDate(invalidEntryName));
        Assert.assertNull(ks.getEntry(invalidEntryName, empryProtectionParam));
        Assert.assertNull(ks.getKey(invalidEntryName, "".toCharArray()));

        // try to delete an entry that does not exist
        exceptionThrown = false;
        try {
            ks.deleteEntry(invalidEntryName);
        } catch (SecurityException e) {
            Assert.fail();
        } catch (KeyStoreException e) {
            exceptionThrown = true;
            Assert.assertEquals("The specified alias " + invalidEntryName
                    + " does not exist", e.getMessage());
        }
        Assert.assertTrue(exceptionThrown);

    }


    /**
     * @param ks
     * @param entry
     * @param beforeDate
     * @param afterDate
     * @param certAlias
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws UnrecoverableEntryException
     * @throws UnrecoverableKeyException
     */
    private void assertTrustedCertEquals(KeyStore ks, KeyCertificateEntry entry,
            String certAlias)
                    throws KeyStoreException, NoSuchAlgorithmException,
                    UnrecoverableEntryException, UnrecoverableKeyException {

        Assert.assertTrue(ks.isCertificateEntry(certAlias));
        Assert.assertFalse(ks.isKeyEntry(certAlias));
        Assert.assertTrue(ks.entryInstanceOf(certAlias,
                KeyStore.TrustedCertificateEntry.class));
        Assert.assertFalse(ks.entryInstanceOf(certAlias, KeyStore.PrivateKeyEntry.class));
        Assert.assertFalse(ks.entryInstanceOf(certAlias, KeyStore.SecretKeyEntry.class));

        Certificate cert = ks.getCertificate(certAlias);
        Assert.assertNotNull(cert);
        Assert.assertEquals(entry.getCertificateChain()[0], cert);
        Assert.assertEquals(certAlias, ks.getCertificateAlias(cert));
        Assert.assertNull(ks.getCertificateChain(certAlias));
        KeyStore.Entry ksEntry = ks.getEntry(certAlias, null);
        Assert.assertEquals(KeyStore.TrustedCertificateEntry.class,
                ksEntry.getClass());
        KeyStore.TrustedCertificateEntry trustedCertEntry =
                (KeyStore.TrustedCertificateEntry) ksEntry;
        Assert.assertEquals(entry.getCertificateChain()[0],
                trustedCertEntry.getTrustedCertificate());


        Assert.assertNull(ks.getKey(certAlias, null));
    }

    /**
     * @param ks
     * @param entry
     * @param beforeDate
     * @param afterDate
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws UnrecoverableEntryException
     */
    private void assertKeyCertificateEntryEquals(KeyStore ks, KeyCertificateEntry entry)
            throws KeyStoreException, NoSuchAlgorithmException,
            UnrecoverableEntryException {

        KeyStore.PasswordProtection empryProtectionParam =
                new KeyStore.PasswordProtection("123".toCharArray());

        KeyStore.Entry ksEntry =
                ks.getEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                        empryProtectionParam);

        Assert.assertTrue(ksEntry instanceof KeyStore.PrivateKeyEntry);
        Assert.assertTrue(ksEntry instanceof KeyStore.PrivateKeyEntry);
        KeyStore.PrivateKeyEntry ksKey = (PrivateKeyEntry) ksEntry;
        Assert.assertEquals(
                KeyCertificatePairGenerator.loadPrivateKeyFromBytes(entry.getKey()),
                ksKey.getPrivateKey());
        Assert.assertArrayEquals(entry.getCertificateChain(), ksKey.getCertificateChain());

        Assert.assertArrayEquals(entry.getCertificateChain(),
                ks.getCertificateChain(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS));
        Assert.assertArrayEquals(entry.getKey(),
                ks.getKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, null)
                .getEncoded());
        Assert.assertTrue(ks.entryInstanceOf(
                KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                KeyStore.PrivateKeyEntry.class));
        Assert.assertFalse(ks.entryInstanceOf(
                KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                KeyStore.SecretKeyEntry.class));
        Assert.assertFalse(ks.entryInstanceOf(
                KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                KeyStore.TrustedCertificateEntry.class));

        Assert.assertEquals(entry.getCertificateChain()[0],
                ks.getCertificate(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS));
        Assert.assertEquals(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS,
                ks.getCertificateAlias(entry.getCertificateChain()[0]));
        Assert.assertFalse(ks
                .isCertificateEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS));
        Assert.assertTrue(ks.isKeyEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS));
    }

    private void assertCreationDateInTImeRange(KeyStore ks, String alias,
            Date beforeDate, Date afterDate) throws KeyStoreException {
        Date creationDate = ks.getCreationDate(alias);
        Assert.assertTrue(creationDate.after(beforeDate));
        Assert.assertTrue(creationDate.before(afterDate));
    }

    private void assertAliasesEquals(KeyStore ks, List<String> expectedAliases)
            throws KeyStoreException {
        // the sizeof the keystore should be 1 on startup
        Assert.assertEquals(expectedAliases.size(), ks.size());

        List<String> aliases = Collections.list(ks.aliases());
        Assert.assertEquals(expectedAliases.size(), aliases.size());
        for (String expectedAlias : expectedAliases) {
            Assert.assertTrue(aliases.contains(expectedAlias));
            Assert.assertTrue(ks.containsAlias(expectedAlias));
        }
    }

    private void assertAliasesIn(KeyStore ks, List<String> expectedAliases)
            throws KeyStoreException {

        for (String expectedAlias : expectedAliases) {
            Assert.assertTrue(ks.containsAlias(expectedAlias));
        }
    }
}
