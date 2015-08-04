/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.ssl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.emc.storageos.coordinator.client.service.*;
import com.emc.storageos.security.keystore.impl.DistributedKeyStoreImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;

/**
 * The official ViPR trust manager. Decides whether or not to accept certificates
 * according to trust material in the ViPR keystore, and the system settings.
 * NOTE: this object cannot be injected by spring, since it has a dependency on the keystore
 */
public class ViPRX509TrustManager implements X509TrustManager {
    private final static String X509_ALGORITHM = "SunX509";

    private static Logger log = LoggerFactory.getLogger(ViPRX509TrustManager.class);
    private X509TrustManager defaultViPRTrustManager;
    private KeyStore keystore;
    private final CoordinatorConfigStoringHelper coordConfigStoringHelper;

    public ViPRX509TrustManager(CoordinatorClient coordinator) {
        coordConfigStoringHelper = new CoordinatorConfigStoringHelper(coordinator);
        try {
            keystore = KeyStoreUtil.getViPRKeystore(coordinator);
        } catch (GeneralSecurityException | IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        loadTrustManager();

        addTrustStoreListener(coordinator);
    }

    private void addTrustStoreListener(CoordinatorClient coordinator) {
        try {
            coordinator.addNodeListener(new TrustStoreListener());
        } catch (Exception e) {
            log.error("Fail to add TrustStoreListener.", e);
        }
    }

    /**
     * loads the trust manager using the vipr keystore.
     */
    private synchronized void loadTrustManager() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(X509_ALGORITHM);
            tmf.init(keystore);

            for (TrustManager trustManager : tmf.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager) {
                    defaultViPRTrustManager = (X509TrustManager) trustManager;
                    log.debug("found a X509TrustManager instance");
                    break;
                }
            }

            log.info("renew trust manager. the # of certificates in trust store is {}",
                    defaultViPRTrustManager.getAcceptedIssuers().length);
        } catch (GeneralSecurityException e) {
            log.error(e.getMessage(), e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#checkClientTrusted(java.security.cert.X509Certificate[], java.lang.String)
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        try {
            defaultViPRTrustManager.checkClientTrusted(chain, authType);
        } catch (CertificateException e) {
            log.debug("Client certificate was not trusted by default trust manager, checking accept all certs config. Certificate: "
                    + chain[0]);
            // if setting for accepting all connections is set to true
            if (KeyStoreUtil.getAcceptAllCerts(coordConfigStoringHelper)) {
                log.warn("The following certificate is not trusted." + chain[0]);
            } else {
                log.debug(
                        "Accept all certs is set to false, the certificate will not be trusted",
                        e);
                throw e;
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.X509Certificate[], java.lang.String)
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        try {
            defaultViPRTrustManager.checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
            log.debug("Server certificate was not trusted by default trust manager, checking accept all certs config. Certificate: "
                    + chain[0]);
            // if setting for accepting all connections is set to true
            if (KeyStoreUtil.getAcceptAllCerts(coordConfigStoringHelper)) {
                log.warn("The following certificate is not trusted." + chain[0]);
            } else {
                log.debug(
                        "Accept all certs is set to false, the certificate will not be trusted",
                        e);
                throw e;
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        X509Certificate[] acceptedIssuers = defaultViPRTrustManager.getAcceptedIssuers();
        List<String> names = new ArrayList<String>();
        for (X509Certificate x509Certificate : acceptedIssuers) {
            names.add(x509Certificate.getIssuerDN().getName());
        }
        log.debug("Accepted issuers are: " + names);
        return acceptedIssuers;
    }

    /**
     * the listener class to listen the trust store node change.
     */
    class TrustStoreListener implements NodeListener {

        public String getPath() {

            String path = String.format("/config/%s/%s",
                    DistributedKeyStoreImpl.TRUSTED_CERTIFICATES_CONFIG_KIND,
                    DistributedKeyStoreImpl.UPDATE_LOG);
            return path;
        }

        /**
         * called when user add/remove certificate from trust store.
         */
        @Override
        public void nodeChanged() {

            log.info("Trust store changed. renewing the trust manager " + defaultViPRTrustManager);
            loadTrustManager();
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {

            if (state.equals(State.CONNECTED)) {
                log.info("Connection reconnected. reloading the trust manager " + defaultViPRTrustManager);
                loadTrustManager();
            }
        }
    }
}
