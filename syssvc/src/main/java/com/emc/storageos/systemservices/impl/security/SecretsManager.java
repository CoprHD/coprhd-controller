/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.security;

import java.net.URI;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.Key;
import java.security.KeyStore;
import java.util.Map;

import com.emc.storageos.security.ssh.PEMUtil;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.property.Notifier;
import com.emc.storageos.systemservices.impl.resource.ConfigService;
import com.emc.storageos.systemservices.impl.util.AbstractManager;

import static com.emc.storageos.coordinator.client.model.Constants.HIDDEN_TEXT_MASK;

/**
 * Configure ssh and ssl during syssvc booting up.
 * 1. Generate ssh keys and config files and put them in system properties for following boots.
 * 2. Dump ssl key store and trust store into cached properties in bootfs
 * Internally there is a background thread making sure ssl properties on bootfs consistent with ones in zk.
 */
public class SecretsManager extends AbstractManager {

    private static final Logger log = LoggerFactory.getLogger(SecretsManager.class);

    private static final String NGINX_PUB_KEY = "nginx_pub_key";
    private static final String NGINX_PRIV_KEY = "nginx_priv_key";
    private static final String NGINX_KEY_HASH = "nginx_key_hash";
    private static final String SSL_PROP_TAG = "ssl";

    private String key;
    private String cert;
    private String localKey;
    private String keyHash;
    private String localKeyHash;

    @Autowired
    private SshConfigurator sshConfig;

    @Override
    protected URI getWakeUpUrl() {
        return SysClientFactory.URI_WAKEUP_SECRETS_MANAGER;
    }

    @Override
    protected void innerRun() {
        while (doRun) {
            log.debug("Main loop: Start");

            // Wait for target info initialized
            PropertyInfoExt targetInfo = coordinator.getTargetInfo(PropertyInfoExt.class);
            if (targetInfo == null) {
                log.info("The target info in ZK has not been initialized yet. Waiting...");
                retrySleep();
                continue;
            }

            // Step0: Configure ssh if needed. Probably only run once in first boot.
            try {
                sshConfig.run();
            } catch (Exception e) {
                log.info("Error during attempt to configure ssh will be retried: {}", e.getMessage());
                retrySleep();
                continue;
            }

            // Step1:
            log.info("Step1: Sync SSL key and certificate if needed");
            try {
                syncSslKeyAndCert();
            } catch (Exception e) {
                log.info("Step1 failed and will be retried: {}", e.getMessage());
                retrySleep();
                continue;
            }

            // Step2: sleep
            log.info("Step2: sleep");
            longSleep();
        }
    }

    private void syncSslKeyAndCert() throws Exception {

        syncTargetKeyAndCert();
        syncLocalKey();

        if (escapeNewlines(key).equals(localKey) && keyHash.equals(localKeyHash)) {
            log.info("Key in coordinatorsvc and local bootfs are sync. No need to rewrite");
            return;
        }

        updateLocalSslProps();
    }

    private String generateKeyHash(String key) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(key.getBytes());
        byte[] digest = md.digest();
        return new String(Base64.encodeBase64(digest));
    }

    private String getAndEncodePrivateKey(KeyStore keyStore) throws Exception {
        Key key = keyStore.getKey(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, null);
        return PEMUtil.encodePrivateKey(key.getEncoded());
    }

    private String getAndEncodeCert(KeyStore keyStore) throws Exception {
        Certificate[] certificates = keyStore.getCertificateChain(
                KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
        return KeyCertificatePairGenerator.getCertificateChainAsString(certificates);
    }

    /**
     * The newlines in the external file need to be replaced by "\\n"s
     * so that: a) it remains a one-liner in the /etc/systool --getprops output
     * b) when _get_prop2 is called in /etc/genconfig, they can be converted back
     * to actual newlines.
     * The reason we must use "\\n" instead of "\n" here is that _get_props2 references
     * ${_GENCONFIG_PROPS} without the double quotes
     */
    private static String escapeNewlines(String string) {
        return string.replace("\n", "\\\\n");
    }

    private void updateLocalSslProps() throws Exception {

        PropertyInfoExt sslProps = new SslPropertyInfo();
        sslProps.addProperty(NGINX_PRIV_KEY, escapeNewlines(key));
        sslProps.addProperty(NGINX_PUB_KEY, escapeNewlines(cert));
        log.info("updating local ssl properties");
        localRepository.setSslPropertyInfo(sslProps);

        log.info("Reconfiguring SSL related config files");
        localRepository.reconfigProperties(SSL_PROP_TAG);

        log.info("Invoking SSL notifier");
        Notifier.getInstance(SSL_PROP_TAG).doNotify();

        sslProps.addProperty(NGINX_KEY_HASH, keyHash);
        log.info("updating local ssl key hash property");
        localRepository.setSslPropertyInfo(sslProps);
    }

    private void syncTargetKeyAndCert() throws Exception {
        KeyStore viprKeystore = KeyStoreUtil.getViPRKeystore(coordinator.getCoordinatorClient());
        key = getAndEncodePrivateKey(viprKeystore);
        cert = getAndEncodeCert(viprKeystore);
        keyHash = generateKeyHash(key);
    }

    private void syncLocalKey() throws Exception {
        PropertyInfoExt localSslInfo = localRepository.getSslPropertyInfo();
        localKey = localSslInfo.getProperty(NGINX_PRIV_KEY);
        localKeyHash = localSslInfo.getProperty(NGINX_KEY_HASH);
    }

    /**
     * All SSL properties should be encrypted but certificate_version.
     */
    private static class SslPropertyInfo extends PropertyInfoExt {

        @Override
        public String toString() {
            return toString(true);
        }

        /**
         * Different from PropertyInfoExt, if masking property output only depends on the flag withMask
         * and regardless to global metadata.
         * 
         * @param withMask if true, replace all encrypted string with HIDDEN_TEXT_MASK,
         *            otherwise always print the real content.
         * @return
         */
        @Override
        public String toString(boolean withMask) {
            StringBuffer sb = new StringBuffer();
            for (Map.Entry<String, String> entry : getProperties().entrySet()) {
                sb.append(entry.getKey());
                sb.append(ENCODING_EQUAL);
                // Hide encrypted string in audit log
                if (entry.getKey().equals(ConfigService.CERTIFICATE_VERSION)) {
                    sb.append(entry.getValue());
                } else if (withMask) {
                    sb.append(HIDDEN_TEXT_MASK);
                } else {
                    sb.append(entry.getValue());
                }
                sb.append(ENCODING_NEWLINE);
            }
            return sb.toString();
        }
    }
}
