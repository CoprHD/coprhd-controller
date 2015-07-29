/*
 * Copyright (c) 2009 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.http.ssl;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * A TrustManager which trusts everyone by default.
 * <p>
 * Testing with self-signed and expired certificates can be problematic. This TrustManager implementation allows the use of untrusted and
 * expired certificates for testing purposes.
 * <p>
 * WARNING: Do *not* use in production!
 * 
 * @author Ben Perkins
 */
public class PermissiveX509TrustManager implements X509TrustManager {
    private X509TrustManager defaultTrustManager = null;
    private boolean allowUntrusted = true;

    /**
     * Construct a TrustManager instance
     * 
     * @param keystore keystore to use. if null default will be used
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    public PermissiveX509TrustManager(KeyStore keystore) throws NoSuchAlgorithmException, KeyStoreException {
        super();
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keystore);
        TrustManager[] trustmanagers = factory.getTrustManagers();
        if (trustmanagers.length == 0) {
            throw new NoSuchAlgorithmException("no trust managers available");
        }
        this.defaultTrustManager = (X509TrustManager) trustmanagers[0];
    }

    public X509Certificate[] getAcceptedIssuers() {
        return this.defaultTrustManager.getAcceptedIssuers();
    }

    public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        if (!allowUntrusted) {
            defaultTrustManager.checkClientTrusted(certificates, authType);
        }
    }

    public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        if (!allowUntrusted) {
            defaultTrustManager.checkServerTrusted(certificates, authType);
        }
    }

    /**
     * Direct the trust manager to allow untrusted certificates or not.
     * <p>
     * 
     * @param allowUntrusted true if untrusted certificates should be allowed, otherwise false
     */
    public void setAllowUntrusted(boolean allowUntrusted) {
        this.allowUntrusted = allowUntrusted;
    }

    /**
     * Determine if the trust manager is currently allowing untrusted certificates.
     * <p>
     * Default value is true.
     * <p>
     * 
     * @return true if we're allowing everyone, otherwise false
     */
    public boolean getAllowUntrusted() {
        return allowUntrusted;
    }
}
