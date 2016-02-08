/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore.impl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.exceptions.RetryableSecurityException;

/**
 * Helper methods for keystore related tasks
 */
public class KeyStoreUtil {

    private static final String TRUSTSTORE_SETTINGS_ID = "truststore_settings_id";
    private static final String ACCEPT_ALL_CERTIFICATES_KEY =
            "accept_all_certificates_entry";

    private static final int MAX_NUMBER_OF_RETRIES = 20;
    private static final long TIME_TO_WAIT_IN_MILLIS = 3000;

    private static Logger log = LoggerFactory.getLogger(KeyStoreUtil.class);

    private static KeyStore keyStoreInst = null;

    public static synchronized KeyStore getViPRKeystore(CoordinatorClient coordinator)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
            IOException, InterruptedException {

        // make keystore singleton.
        if (keyStoreInst != null) {
            return keyStoreInst;
        }

        DistributedLoadKeyStoreParam loadStoreParam = new DistributedLoadKeyStoreParam();
        loadStoreParam.setCoordinator(coordinator);
        KeyStore viprKeyStore =
                KeyStore.getInstance(SecurityProvider.KEYSTORE_TYPE,
                        new SecurityProvider());
        boolean continueLoading = true;
        int numberOfTries = 0;
        while (continueLoading && numberOfTries < MAX_NUMBER_OF_RETRIES) {
            try {
                viprKeyStore.load(loadStoreParam);
                continueLoading = false;
            } catch (RetryableSecurityException e) {
                numberOfTries++;
                log.info("Could not load keystore, waiting " + TIME_TO_WAIT_IN_MILLIS
                        + " ms. Attempt #" + numberOfTries, e);
                Thread.sleep(TIME_TO_WAIT_IN_MILLIS);
            }
        }

        keyStoreInst = viprKeyStore;

        return viprKeyStore;
    }

    /**
     * Sets whether the stored certificate is self generated
     * 
     * @param coordConfigStoringHelper
     * @param selfGenerated
     */
    public static void setSelfGeneratedCertificate(CoordinatorConfigStoringHelper coordConfigStoringHelper, Boolean selfGenerated) {
        try {
            coordConfigStoringHelper.createOrUpdateConfig(selfGenerated,
                    DistributedKeyStoreImpl.KEY_CERTIFICATE_PAIR_LOCK,
                    coordConfigStoringHelper.getSiteId(),
                    DistributedKeyStoreImpl.KEY_CERTIFICATE_PAIR_CONFIG_KIND,
                    DistributedKeyStoreImpl.KEY_CERTIFICATE_PAIR_ID,
                    DistributedKeyStoreImpl.IS_SELF_GENERATED_KEY);
            log.debug(DistributedKeyStoreImpl.IS_SELF_GENERATED_KEY + " set to "
                    + selfGenerated);
        } catch (Exception e) {
            log.warn("failed to set " + DistributedKeyStoreImpl.IS_SELF_GENERATED_KEY
                    + " to " + selfGenerated, e);
        }
    }

    /**
     * Sets whether the trust manager should accept all certificates
     * 
     * @param coordConfigStoringHelper
     * @param selfGenerated
     * @throws Exception
     */
    public static void setAcceptAllCertificates(CoordinatorConfigStoringHelper coordConfigStoringHelper,
            Boolean acceptAllCerts) throws Exception {
        coordConfigStoringHelper.createOrUpdateConfig(acceptAllCerts,
                DistributedKeyStoreImpl.TRUSTED_CERTIFICATES_LOCK,
                DistributedKeyStoreImpl.TRUSTED_CERTIFICATES_CONFIG_KIND,
                TRUSTSTORE_SETTINGS_ID, ACCEPT_ALL_CERTIFICATES_KEY);
        log.debug(ACCEPT_ALL_CERTIFICATES_KEY + " set to " + acceptAllCerts);
    }

    /**
     * gets the value of the system for accepting all connections. If it fails to get the
     * system property it defaults to true.
     * 
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static boolean getAcceptAllCerts(CoordinatorConfigStoringHelper coordConfigStoringHelper) {
        Boolean accepetAllCert = null;
        try {
            accepetAllCert =
                    coordConfigStoringHelper.readConfig(
                            DistributedKeyStoreImpl.TRUSTED_CERTIFICATES_CONFIG_KIND,
                            TRUSTSTORE_SETTINGS_ID, ACCEPT_ALL_CERTIFICATES_KEY);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        if (accepetAllCert == null) {
            try {
                KeyStoreUtil.setAcceptAllCertificates(coordConfigStoringHelper, Boolean.TRUE);
            } catch (Exception e) {
                log.warn("failed to set " + ACCEPT_ALL_CERTIFICATES_KEY
                        + "to true.");
            }
            return true;
        }
        return accepetAllCert;
    }

    /**
     * @param coordConfigStoringHelper
     * @return
     */
    public static Boolean isSelfGeneratedCertificate(
            CoordinatorConfigStoringHelper coordConfigStoringHelper) {
        try {
            Boolean selfGenerated =
                    coordConfigStoringHelper.readConfig(
                            coordConfigStoringHelper.getSiteId(),
                            DistributedKeyStoreImpl.KEY_CERTIFICATE_PAIR_CONFIG_KIND,
                            DistributedKeyStoreImpl.KEY_CERTIFICATE_PAIR_ID,
                            DistributedKeyStoreImpl.IS_SELF_GENERATED_KEY);
            return selfGenerated;
        } catch (Exception e) {
            log.warn(
                    "Failed to read if certificate is self generated, defaulting to false.",
                    e);
            return false;
        }
    }
}
