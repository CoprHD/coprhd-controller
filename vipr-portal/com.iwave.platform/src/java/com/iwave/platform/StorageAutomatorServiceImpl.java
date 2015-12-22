/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.AbstractSecuredWebServer;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.validator.Validator;

public class StorageAutomatorServiceImpl extends AbstractSecuredWebServer implements StorageAutomatorService {

    private static final Logger log = LoggerFactory.getLogger(StorageAutomatorServiceImpl.class);

    @Autowired
    private CoordinatorClient coordinatorClient;

    @Autowired
    private AuthSvcEndPointLocator authSvcEndPointLocator;

    @Autowired
    private ServiceBeacon serviceBeacon;
    
    @Override
    public void start() throws Exception {
        log.info("Starting sasvc service");
        initValidator();
        initServer();
        _server.start();
        serviceBeacon.start();
        log.info("Starting sasvc service done");
    }

    @Override
    public void stop() throws Exception {
        log.info("Stopping sasvc service");
        _server.stop();
        _dbClient.stop();
        log.info("Stopping sasvc service done");
    }

    private void initValidator() {
        Validator.setCoordinator(coordinatorClient);
        Validator.setAuthSvcEndPointLocator(authSvcEndPointLocator);
    }

}
