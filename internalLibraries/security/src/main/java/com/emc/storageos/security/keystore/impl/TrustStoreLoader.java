/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.keystore.impl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.keystore.DistributedKeyStore;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Load the trust store from local file system to zookeeper.
 */
public class TrustStoreLoader {

    private static final Logger log = LoggerFactory.getLogger(TrustStoreLoader.class);

    private static final String CA_CERTS_LOCK = "caCertsLock";
    private static final String CA_CERTS_CONFIG_LOCK = "caCertsConfigLock";

    private String tsVersionFilePath;

    private String caCertFile;

    private CoordinatorClient coordinatorClient;

    private CoordinatorConfigStoringHelper coordHelper;

    private CertificateFactory certFactory = null;

    public void setTsVersionFilePath(String tsVersionFilePath) {
        this.tsVersionFilePath = tsVersionFilePath;
    }

    public void setCaCertFile(String caCertFile) {
        this.caCertFile = caCertFile;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public void setCoordHelper(CoordinatorConfigStoringHelper coordHelper) {
        this.coordHelper = coordHelper;
    }

    public void load() {

        InterProcessLock tsLock = null;

        try {
            /*
             * the lock and version check to make sure, within one vdc only one service which uses truststore (like authsvc)
             * fill up zk truststore at same time.
             */
            log.info("Loading the builtin trust store ...");
            tsLock = coordHelper.acquireLock(CA_CERTS_LOCK);

            if (compareTrustStoreVersion()) { // same version in zk and local
                log.info("CA certs version match, no need to do anything.");
                return;
            }

            log.info("CA certs version doesn't match, need to load root certs from file to zk.");
            loadCertsFromLocalKeyStore();

            addVersionInZK();

            log.info("Loaded the builtin trust store successfully");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            coordHelper.releaseLock(tsLock);
        }
    }

    private void addVersionInZK() {

        try {
            String version = getVersionFromFile();

            coordHelper.createOrUpdateConfig(version,
                    CA_CERTS_CONFIG_LOCK,
                    DistributedKeyStoreImpl.CA_CERTIFICATES_CONFIG_KIND,
                    DistributedKeyStoreImpl.CA_CERTIFICATES_CONFIG_ID,
                    DistributedKeyStoreImpl.CA_CERTIFICATES_CONFIG_KEY_VERSION
                    );
            log.info("saved the new version of ca certs to ZK: {}", version);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getVersionFromFile() {

        File tsVersionFile = new File(tsVersionFilePath);
        if (!tsVersionFile.exists()) {
            throw new RuntimeException("The version file of trust store not found: " + tsVersionFilePath);
        }

        BufferedReader in = null;

        try {
            in = new BufferedReader(new FileReader(tsVersionFile));
            return in.readLine().trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                log.error("Unexpected error", e);
            }
        }
    }

    private boolean compareTrustStoreVersion() {
        String localVersion = getVersionFromFile();
        String zkVersion = getVersionFromZK();

        log.info("The local version of cacerts is [{}], the zk version is [{}].", localVersion, zkVersion);

        if (zkVersion == null) {
            return false;
        }

        // return true if zk version is newer or equal
        return (Integer.parseInt(localVersion) <= Integer.parseInt(zkVersion)) ? true : false;
    }

    private String getVersionFromZK() {
        try {
            String v = coordHelper.readConfig(
                    DistributedKeyStoreImpl.CA_CERTIFICATES_CONFIG_KIND,
                    DistributedKeyStoreImpl.CA_CERTIFICATES_CONFIG_ID,
                    DistributedKeyStoreImpl.CA_CERTIFICATES_CONFIG_KEY_VERSION);

            return (v == null) ? null : v.trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void loadCertsFromLocalKeyStore() {

        log.info("Adding root certificates from " + caCertFile + " into ViPR.");

        Map<String, TrustedCertificateEntry> cacerts = loadAllRootCerts();

        writeCertsToViPRKeyStore(cacerts);

        log.info("Added " + cacerts.keySet().size() + " root certificates into ViPR.");
    }

    private Map<String, TrustedCertificateEntry> loadAllRootCerts() {

        Map<String, TrustedCertificateEntry> cacerts = new HashMap<>();

        try {
            // load key store from file
            FileInputStream is = new FileInputStream(caCertFile);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(is, null);

            Enumeration<String> aliases = keyStore.aliases();

            // put all certs in the map. the key is the hash value of each cert string.
            while (aliases.hasMoreElements()) {

                String als = aliases.nextElement();
                X509Certificate cert = (X509Certificate) keyStore.getCertificate(als);

                String alias = DigestUtils.sha512Hex(cert.getEncoded()) + KeystoreEngine.SUFFIX_VIPR_SUPPLY_CERT;
                cacerts.put(alias, new TrustedCertificateEntry(cert, new Date()));
            }

            return cacerts;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeCertsToViPRKeyStore(Map<String, TrustedCertificateEntry> cacerts) {

        DistributedKeyStore zkKeystore = new DistributedKeyStoreImpl();

        DistributedLoadKeyStoreParam loadStoreParam = new DistributedLoadKeyStoreParam();
        loadStoreParam.setCoordinator(coordinatorClient);

        zkKeystore.init(loadStoreParam);
        zkKeystore.setCACertificates(cacerts);
    }

}
