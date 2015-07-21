/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.impl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.security.keystore.impl.DistributedLoadKeyStoreParam;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Enumeration;

/**
 * Handle keystore commands
 */
public class CassandraKeystoreHandler {

    private static final Logger log = LoggerFactory.getLogger(CassandraKeystoreHandler.class);

    private char[] password;
    private static String keyAlias = KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS;

    private String keyStoreFile;
    private String trustStoreFile;

    private final KeyStore keystore;
    private final CoordinatorClient coordinator;

    public CassandraKeystoreHandler(CoordinatorClient coordinator, String keyStoreFile, String trustStoreFile, String password)
            throws KeyStoreException, NoSuchAlgorithmException,  CertificateException, IOException, InterruptedException {
        this.coordinator = coordinator;
        keystore = KeyStoreUtil.getViPRKeystore(coordinator);

        this.keyStoreFile = keyStoreFile;
        this.trustStoreFile = trustStoreFile;
        this.password = password.toCharArray();
    }

    public Key getViPRKey() throws Exception {
        Key viprKey =
                keystore.getKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, null);
        return viprKey;
    }

    public Certificate[] getViPRCertificate() throws Exception {
        Certificate[] viprCertificateChain =
                keystore.getCertificateChain(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
        return viprCertificateChain;
    }
    
    public void saveKeyStore() throws Exception {
        log.info("Trying to generate keystore {}", keyStoreFile);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, password);
        ks.setKeyEntry(keyAlias,
                keystore.getKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, null),
                password, keystore.getCertificateChain(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS));
        ks.store(new FileOutputStream(keyStoreFile), password);
        log.info("The keystore file {} is generated successfully.",keyStoreFile);
    }
    
    public void saveTrustStore() throws Exception {
        log.info("Trying to generate truststore {}", trustStoreFile);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, password);
        
        DistributedLoadKeyStoreParam loadStoreParam;
        loadStoreParam = new DistributedLoadKeyStoreParam();
        loadStoreParam.setCoordinator(coordinator);
        KeystoreEngine ksEngine = new KeystoreEngine();
        ksEngine.engineLoad(loadStoreParam);
        Enumeration<String> allAliases = ksEngine.engineAliases();
        while (allAliases.hasMoreElements()) {
            String alias = (String) allAliases.nextElement();
            KeyStore.TrustedCertificateEntry trustedCertEntry = new KeyStore.TrustedCertificateEntry(
                    keystore.getCertificate(alias));
            ks.setEntry(alias, trustedCertEntry, null);
        }
        ks.store(new FileOutputStream(trustStoreFile), password);
        log.info("The truststore file {} is generated successfully.", trustStoreFile);
    }
}
