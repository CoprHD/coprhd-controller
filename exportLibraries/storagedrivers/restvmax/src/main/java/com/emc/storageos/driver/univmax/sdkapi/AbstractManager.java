/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.AuthenticationInfo;
import com.emc.storageos.driver.univmax.RegistryHandler;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.RestHandler;
import com.emc.storageos.driver.univmax.rest.UrlGenerator;
import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

public abstract class AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(AbstractManager.class);

    RestClient client;
    LockManager lockManager;
    RegistryHandler registryHandler;
    AuthenticationInfo authenticationInfo;
    RestHandler restHandler;

    /**
     * @param driverRegistry
     * @param lockManager
     */
    public AbstractManager(Registry driverRegistry, LockManager lockManager) {
        this.lockManager = lockManager;
        this.registryHandler = new RegistryHandler(driverRegistry);
    }

    /**
     * TODO: only used for test
     * 
     * @param client the client to set
     */
    public void setClient(RestClient client) {
        this.client = client;
    }

    /**
     * TODO: only used for test
     * 
     * @param restHandler
     */
    public void setRestHandler(RestHandler restHandler) {
        this.restHandler = restHandler;
    }

    /**
     * TODO: only used for test
     * 
     * @param authenticationInfo the authenticationInfo to set
     */
    public void setAuthenticationInfo(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
    }

    boolean initializeRestClient(String arrayId) {
        authenticationInfo = registryHandler.getAccessInfo(arrayId);
        if (authenticationInfo == null) {
            log.error("Failed to find AuthenticationInfo for array {}", arrayId);
            return false;
        }
        client = new RestClient(authenticationInfo.getProtocol(), authenticationInfo.getHost(), authenticationInfo.getPort(),
                authenticationInfo.getUserName(),
                authenticationInfo.getPassword());

        restHandler = new RestHandler(client);
        return true;
    }

    List<String> genUrlFillersWithSn(String... fillers) {
        List<String> urlFillers = UrlGenerator.genUrlFillers(fillers);
        urlFillers.add(0, authenticationInfo.getSn());
        return urlFillers;
    }

    /**
     * Print error message out.
     * 
     * @param responseBean
     */
    void printErrorMessage(GenericResultImplType responseBean) {
        if (!responseBean.isSuccessfulStatus()) {
            log.error("httpCode {}: {}", responseBean.getHttpCode(), responseBean.getMessage());
        }
    }
}
