/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.PathConstants.KEYSTORE_URL;

import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.keystore.CertificateChain;
import com.emc.vipr.model.keystore.KeyAndCertificateChain;
import com.emc.vipr.model.keystore.RotateKeyAndCertParam;

/**
 * Keystore resource.
 * <p>
 * Base URL: <tt>/keystore</tt>
 */
public class Keystore {

    protected final RestClient client;

    public Keystore(RestClient client) {
        this.client = client;
    }

    /**
     * Gets the certificate chain that identifies ViPR.
     * <p>
     * API Call: <tt>GET /keystore</tt>
     * 
     * @return The Certificate chain
     */
    public CertificateChain getCertificateChain() {
        return client.get(CertificateChain.class, KEYSTORE_URL);
    }

    /**
     * Sets the key and certificate chain that ViPR uses for SSL communication.
     * <p>
     * API Call: <tt>PUT /vdc/keystore</tt>
     * 
     * @param keyAndCert
     *            the new key and certificate
     * 
     * @return The Certificate chain
     */
    public CertificateChain setKeyAndCertificateChain(KeyAndCertificateChain keyAndCert) {
        RotateKeyAndCertParam rotateKeyAndCertParam = new RotateKeyAndCertParam();
        rotateKeyAndCertParam.setSystemSelfSigned(false);
        rotateKeyAndCertParam.setKeyCertChain(keyAndCert);
        return client.put(CertificateChain.class, rotateKeyAndCertParam, KEYSTORE_URL);
    }

    /**
     * Makes ViPR generate a key and a self signed certificate to use for SSL
     * communication.
     * <p>
     * API Call: <tt>PUT /vdc/keystore</tt>
     * 
     * @return The Certificate chain
     */
    public CertificateChain regenerateKeyAndCertificate() {
        RotateKeyAndCertParam rotateKeyAndCertParam = new RotateKeyAndCertParam();
        rotateKeyAndCertParam.setSystemSelfSigned(true);
        return client.put(CertificateChain.class, rotateKeyAndCertParam, KEYSTORE_URL);
    }

}

