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
package com.emc.storageos.security.keystore.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.KeyStore.LoadStoreParameter;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.Map.Entry;

import com.emc.storageos.security.helpers.SecurityService;
import com.emc.storageos.security.helpers.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.keystore.DistributedKeyStore;
import com.emc.storageos.security.keystore.KeystoreParam;

/**
 * Implements JAVA's KeyStoreSpi and allows us to use java's Security framework with our
 * own implementation
 */
public class KeystoreEngine extends KeyStoreSpi {

    private static Logger log = LoggerFactory.getLogger(KeystoreEngine.class);

    public static final String ViPR_KEY_AND_CERTIFICATE_ALIAS =
            "ViPR_KEY_AND_CERTIFICATE";

    public static final String SUFFIX_VIPR_SUPPLY_CERT = "@@VIPR";

    // the keystore in zk.
    private DistributedKeyStore distributedKeyStore;

    private KeyCertificatePairGenerator keyGen = new KeyCertificatePairGenerator();

    private SecurityService secService;

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineAliases()
     */
    @Override
    public Enumeration<String> engineAliases() {

        log.debug("engineAliases called");

        List<String> allAliases = new ArrayList<String>();

        allAliases.add(ViPR_KEY_AND_CERTIFICATE_ALIAS);

        Set<String> userAddCertsAliases = distributedKeyStore.getTrustedCertificates().keySet();
        Set<String> viprSuppliedCertsAliases = distributedKeyStore.getCACertificates().keySet();

        allAliases.addAll(userAddCertsAliases);
        allAliases.addAll(viprSuppliedCertsAliases);

        log.debug("engineAliases called. vipr ca certs # = {}, user added certs # = {}",
                viprSuppliedCertsAliases.size(), userAddCertsAliases.size());

        return Collections.enumeration(allAliases);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineContainsAlias(java.lang.String)
     */
    @Override
    public boolean engineContainsAlias(String alias) {

        log.debug("engineContainsAlias called ( alias = {} )", alias);

        if (alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS))
            return true;

        if (distributedKeyStore.containsUserAddedCerts(alias))
            return true;

        if (distributedKeyStore.containsViprSuppliedCerts(suffixedAlias(alias)))
            return true;

        return false;
    }

    private String suffixedAlias(String alias) {
        if (alias.endsWith(SUFFIX_VIPR_SUPPLY_CERT)) {
            return alias;
        } else {
            return alias + SUFFIX_VIPR_SUPPLY_CERT;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineDeleteEntry(java.lang.String)
     */
    @Override
    public synchronized void engineDeleteEntry(String alias) throws KeyStoreException {

        log.debug("engineDeleteEntry called ( alias = {} )", alias);

        if (alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS)) {
            throw SecurityException.fatals.ViPRKeyCertificateEntryCannotBeDeleted();
        }

        if (distributedKeyStore.containsUserAddedCerts(alias)) {
            distributedKeyStore.removeTrustedCertificate(alias);
            return;
        }

        if (distributedKeyStore.containsViprSuppliedCerts(suffixedAlias(alias))) {
            distributedKeyStore.removeCACertificate(suffixedAlias(alias));
            return;
        }

        throw new KeyStoreException("The specified alias " + alias
                + " does not exist");
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineGetCertificate(java.lang.String)
     */
    @Override
    public Certificate engineGetCertificate(String alias) {

        log.debug("engineGetCertificate called ( alias = {} )", alias);

        if (alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS)) {
            return getKeyCertificatePair(false).getCertificateChain()[0];
        }

        TrustedCertificateEntry entry = null;

        entry = distributedKeyStore.getUserAddedCert(alias);
        if (entry != null) {
            return entry.getCertificate();
        }

        entry = distributedKeyStore.getViprAddedCert(suffixedAlias(alias));
        if (entry != null) {
            return entry.getCertificate();
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineGetCertificateAlias(java.security.cert.Certificate)
     */
    @Override
    public String engineGetCertificateAlias(Certificate cert) {

        if (cert.equals(getKeyCertificatePair(false).getCertificateChain()[0])) {
            return ViPR_KEY_AND_CERTIFICATE_ALIAS;
        }

        Map<String, TrustedCertificateEntry> userCerts = distributedKeyStore.getTrustedCertificates();
        for (Entry<String, TrustedCertificateEntry> entry : userCerts.entrySet()) {
            if (cert.equals(entry.getValue().getCertificate())) {
                return entry.getKey();
            }
        }

        Map<String, TrustedCertificateEntry> viprCerts = distributedKeyStore.getCACertificates();
        for (Entry<String, TrustedCertificateEntry> entry : viprCerts.entrySet()) {
            if (cert.equals(entry.getValue().getCertificate())) {
                return entry.getKey();
            }
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineGetCertificateChain(java.lang.String)
     */
    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        log.debug("engineGetCertificateChain called ( alias = {} )", alias);

        if (alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS)) {
            return getKeyCertificatePair(false).getCertificateChain();
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineGetCreationDate(java.lang.String)
     */
    @Override
    public Date engineGetCreationDate(String alias) {
        log.debug("engineGetCreationDate called ( alias = {} )", alias);
        if (alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS)) {
            return getKeyCertificatePair(false).getCreationDate();
        }

        TrustedCertificateEntry entry = distributedKeyStore.getUserAddedCert(alias);
        if (entry != null) {
            return entry.getCreationDate();
        }

        entry = distributedKeyStore.getViprAddedCert(alias);
        if (entry != null) {
            return entry.getCreationDate();
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineGetKey(java.lang.String, char[])
     */
    @Override
    public Key engineGetKey(String alias, char[] password)
            throws NoSuchAlgorithmException, UnrecoverableKeyException {

        log.debug("engineGetKey called ( alias = {} )", alias);

        if (!alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS)) {
            return null;
        }
        KeyCertificateEntry entry = getKeyCertificatePair(false);
        return KeyCertificatePairGenerator.loadPrivateKeyFromBytes(entry.getKey());
    }

    /**
     * @return KeyCertificatePair
     */
    private KeyCertificateEntry getKeyCertificatePair(boolean makeKeyNull) {

        log.debug("getKeyCertificatePair called ");

        KeyCertificateEntry pair = distributedKeyStore.getKeyCertificatePair();
        if (makeKeyNull) {
            SecurityUtil.clearSensitiveData(pair.getKey());
        }
        return pair;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineIsCertificateEntry(java.lang.String)
     */
    @Override
    public boolean engineIsCertificateEntry(String alias) {

        log.debug("engineIsCertificateEntry called ( alias = {} )", alias);
        if (alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS)) {
            return false;
        }

        return engineContainsAlias(alias);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineIsKeyEntry(java.lang.String)
     */
    @Override
    public boolean engineIsKeyEntry(String alias) {
        return alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineLoad(java.io.InputStream, char[])
     */
    @Override
    public void engineLoad(InputStream stream, char[] password) throws IOException,
            NoSuchAlgorithmException, CertificateException {
        throw SecurityException.fatals
                .failedToInitializedKeystoreNeedDistKeystoreParams();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineLoad(java.security.KeyStore.LoadStoreParameter)
     */
    @Override
    public void engineLoad(KeyStore.LoadStoreParameter param) throws IOException,
            NoSuchAlgorithmException, CertificateException {

        log.debug("engineLoad called ");

        if (!(param instanceof KeystoreParam)) {
            throw SecurityException.fatals
                    .failedToInitializedKeystoreNeedDistKeystoreParams();
        }
        KeystoreParam scheduledReloadParam = (KeystoreParam) param;
        distributedKeyStore = new DistributedKeyStoreImpl();
        distributedKeyStore.init(scheduledReloadParam);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineSetCertificateEntry(java.lang.String, java.security.cert.Certificate)
     */
    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert)
            throws KeyStoreException {
        if (alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS)) {
            throw SecurityException.fatals.cannotSetTrustedCertificateWithViPRAlias();
        }
        TrustedCertificateEntry entry = new TrustedCertificateEntry(cert, new Date());
        distributedKeyStore.addTrustedCertificate(alias, entry);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineSetKeyEntry(java.lang.String, byte[], java.security.cert.Certificate[])
     */
    @Override
    public synchronized void engineSetKeyEntry(String alias, byte[] key,
            Certificate[] chain)
            throws KeyStoreException {
        if (alias.equalsIgnoreCase(ViPR_KEY_AND_CERTIFICATE_ALIAS)) {
            KeyCertificateEntry entryToSet =
                    new KeyCertificateEntry(key, chain, new Date());
            keyGen.verifyKeyCertificateEntry(entryToSet);
            distributedKeyStore.setKeyCertificatePair(entryToSet);
        } else {
            throw SecurityException.fatals.canOnlyUpdateViPRKeyCertificate();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineSetKeyEntry(java.lang.String, java.security.Key, char[], java.security.cert.Certificate[])
     */
    @Override
    public synchronized void engineSetKeyEntry(String alias, Key key, char[] password,
            Certificate[] chain) throws KeyStoreException {
        engineSetKeyEntry(alias, key.getEncoded(), chain);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineSize()
     */
    @Override
    public int engineSize() {
        return distributedKeyStore.getCACertificates().size() +
                distributedKeyStore.getTrustedCertificates().size() + 1;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * java.security.KeyStoreSpi#engineStore(java.security.KeyStore.LoadStoreParameter)
     */
    @Override
    public void engineStore(LoadStoreParameter param) throws IOException,
            NoSuchAlgorithmException, CertificateException {
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.security.KeyStoreSpi#engineStore(java.io.OutputStream, char[])
     */
    @Override
    public void engineStore(OutputStream stream, char[] password) throws IOException,
            NoSuchAlgorithmException, CertificateException {
        KeyStore ks = loadAndPopulateJKS();

        try {
            logKeystore(ks, password);
            ks.store(stream, password);
        } catch (Exception e) {
            // should not get here since keystore is loaded in loadAndPopulateJKS()
            log.error(e.getMessage(), e);
        }
    }

    private void logKeystore(KeyStore ks, char[] password) throws Exception {

        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String als = aliases.nextElement();
            log.info("Populated a Keystore to be exported has an alias {} ", als);
        }
    }

    /**
     * @return
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     */
    private KeyStore loadAndPopulateJKS()
            throws IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore ks;
        KeyCertificateEntry entry = null;
        Key privateKey = null;
        try {
            ks = KeyStore.getInstance("JKS", "SUN");
            ks.load(null, null);

            entry = getKeyCertificatePair(false);
            privateKey = KeyCertificatePairGenerator.loadPrivateKeyFromBytes(entry.getKey());
            ks.setKeyEntry(ViPR_KEY_AND_CERTIFICATE_ALIAS, privateKey, "changeit".toCharArray(),
                    entry.getCertificateChain());

            for (Entry<String, TrustedCertificateEntry> certEntry : distributedKeyStore.getTrustedCertificates()
                    .entrySet()) {
                ks.setCertificateEntry(certEntry.getKey(), certEntry.getValue()
                        .getCertificate());
            }

            return ks;
        } catch (KeyStoreException | NoSuchProviderException e) {
            throw new CertificateException(e);
        } finally {
            if (entry != null) {
                SecurityUtil.clearSensitiveData(entry.getKey());
            }
            if (privateKey != null) {
                SecurityUtil.clearSensitiveData(privateKey);
            }
        }
    }

    public static boolean isUserSuppliedCerts(String alias) {
        return !alias.endsWith(KeystoreEngine.SUFFIX_VIPR_SUPPLY_CERT);
    }
}
