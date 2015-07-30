/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.model.property.PropertyInfo;

/**
 * Initializes the proxy user password on start. This is needs to run every time the application is started, because the
 * encryption key may have changed due to a clean start.
 * 
 * @author jonnymiller
 */
public class ProxyUserInitializer {
    private static final String PROXY_USER_PASSWORD_PROPERTY = "system_proxyuser_encpassword"; //NOSONAR ("False positive, field does not store a password”)

    private String password;
    @Autowired
    private CoordinatorClient coordinator;
    @Autowired
    @Qualifier("encryptionProvider")
    private EncryptionProvider encryptionProvider;
    @Autowired
    private DbClient dbClient;

    public void setPassword(String password) {
        this.password = password;
    }

    @PostConstruct
    public void init() throws Exception {
        dbClient.start();
        Map<String, String> properties = getProperties();
        String encryptedPassword = encrypt(password);
        properties.put(PROXY_USER_PASSWORD_PROPERTY, encryptedPassword);
        setProperties(properties);
    }

    private String encrypt(String password) {
        return Base64.encodeBase64String(encryptionProvider.encrypt(password));
    }

    private Map<String, String> getProperties() throws Exception {
        PropertyInfo info = coordinator.getPropertyInfo();
        if (info != null) {
            return info.getAllProperties();
        }
        else {
            return new HashMap<>();
        }
    }

    private void setProperties(Map<String, String> properties) throws Exception {
        String str = new PropertyInfoExt(properties).encodeAsString();
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(PropertyInfoExt.TARGET_PROPERTY);
        config.setId(PropertyInfoExt.TARGET_PROPERTY_ID);
        config.setConfig(PropertyInfoExt.TARGET_INFO, str);
        coordinator.persistServiceConfiguration(config);
    }
}
