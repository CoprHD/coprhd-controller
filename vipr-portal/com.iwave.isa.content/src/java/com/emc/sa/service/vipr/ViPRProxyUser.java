/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.vipr.client.AuthClient;

@Component
public class ViPRProxyUser {
    private static final String PROXY_USER = "proxyuser";
    private static final String PROXY_USER_PASSWORD_PROPERTY = "system_proxyuser_encpassword"; // NOSONAR ("False positive, field does not
                                                                                               // store a password”)

    @Autowired
    private CoordinatorClient coordinatorClient;
    @Autowired
    @Qualifier("encryptionProvider")
    private EncryptionProvider encryptionProvider;

    String login(AuthClient client) {
        String encryptedPassword = getProperty(PROXY_USER_PASSWORD_PROPERTY);
        if (StringUtils.isBlank(encryptedPassword)) {
            throw new IllegalArgumentException("Proxy user password is not set");
        }
        String password = encryptionProvider.decrypt(Base64.decodeBase64(encryptedPassword));
        client.getClient().setUsername(PROXY_USER);
        client.getClient().setPassword(password);
        return client.login(PROXY_USER, password);
    }

    private String getProperty(String name) {
        if (coordinatorClient.getPropertyInfo() != null) {
            return coordinatorClient.getPropertyInfo().getProperty(name);
        }
        return null;
    }

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public EncryptionProvider getEncryptionProvider() {
        return encryptionProvider;
    }

    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        this.encryptionProvider = encryptionProvider;
    }
}
