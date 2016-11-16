/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.security.ssl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * This is to skip host name verification when accept all is set
 */
public class CustomHostnameVerifier implements HostnameVerifier {

    Logger log = LoggerFactory.getLogger(CustomHostnameVerifier.class);

    private CoordinatorClient coordinator;

    public CustomHostnameVerifier(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public boolean verify(String s, SSLSession sslSession) {
        log.info("Entering CustomHostnameVerifier. Host name is {} and name from session is {}", s, sslSession.getPeerHost());
        if (KeyStoreUtil.getAcceptAllCerts(new CoordinatorConfigStoringHelper(coordinator))) {
            log.info("The system is set to accept all. Ingore host name verifying");
            return true;
        }
        return false;
    }
}
