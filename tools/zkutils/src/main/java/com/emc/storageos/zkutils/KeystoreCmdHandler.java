/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
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
package com.emc.storageos.zkutils;

import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import com.emc.storageos.security.helpers.SecurityService;
import com.emc.storageos.security.helpers.SecurityUtil;
import com.emc.storageos.security.keystore.KeyStoreExporter;
import com.emc.storageos.security.keystore.impl.*;

import com.emc.storageos.security.ssh.PEMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import org.springframework.context.support.GenericXmlApplicationContext;

/**
 * Handle keystore commands
 */
public class KeystoreCmdHandler {

    private static final Logger log = LoggerFactory.getLogger(KeystoreCmdHandler.class);

    private final KeyStore keystore; // the keystore for RSA only.
    private final CoordinatorClient coordinator;
    private GenericXmlApplicationContext ctx;

    public KeystoreCmdHandler() throws KeyStoreException, NoSuchAlgorithmException,
    CertificateException, IOException, InterruptedException {
        try {
            // To using Spring profile feature
            ctx = new GenericXmlApplicationContext();
            ctx.getEnvironment().setActiveProfiles(System.getProperty("buildType"));
            ctx.load(getContextFiles());
            ctx.refresh();

            coordinator = (CoordinatorClient) ctx.getBean(ZKUtil.COORDINATOR_BEAN);
            keystore = KeyStoreUtil.getViPRKeystore(coordinator);
        } catch (Exception e) {
            log.error("Failed to load the ViPR keystore", e);
            throw e;
        }
    }

    private String[] getContextFiles() {
        return new String[] {ZKUtil.ZKUTI_CONF, "zkutil-oss-conf.xml", "zkutil-emc-conf.xml"};
    }

    public void getViPRKey() {

        Key viprKey = null;

        try {
            viprKey =
                    keystore.getKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, null);
            System.out.print(PEMUtil.encodePrivateKey(viprKey.getEncoded()));
        } catch (Exception e) {
            log.error("Failed to get the ViPR key", e);
        } finally {
            SecurityUtil.clearSensitiveData(viprKey);
        }
    }

    public void getViPRCertificate() {

        Certificate[] viprCertificateChain = null;
        try {
            viprCertificateChain =
                    keystore.getCertificateChain(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
            System.out.print(KeyCertificatePairGenerator
                    .getCertificateChainAsString(viprCertificateChain));
        } catch (Exception e) {
            log.error("Failed to get the ViPR certificate chain", e);

        } finally {
            // clear sensitive data
            for (int i = 0; i < viprCertificateChain.length; i++) {
                SecurityUtil.clearSensitiveData(viprCertificateChain[i].getPublicKey());
            }
        }
    }

    /**
     * Export the ViPR keystore to local file in JKS format
     */
    public void exportKeystore() throws Exception {
        KeyStoreExporter exporter = (KeyStoreExporter) ctx.getBean("keystoreExporter");
        exporter.export();
    }
}
