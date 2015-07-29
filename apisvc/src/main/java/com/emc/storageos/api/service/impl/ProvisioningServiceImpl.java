/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl;

import com.emc.storageos.api.service.ProvisioningService;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.security.AbstractSecuredWebServer;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.authentication.StorageOSUserRepository;
import com.emc.storageos.security.validator.Validator;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Provisioning service default implementation
 */
public class ProvisioningServiceImpl extends AbstractSecuredWebServer implements ProvisioningService {

    @Autowired
    private CoordinatorClient _coordinator;

    @Autowired
    private AuthSvcEndPointLocator _authSvcEndPointLocator;

    @Autowired
    StorageOSUserRepository _repository;

    @Autowired
    ServiceBeacon _svcBeacon;

    @Override
    public synchronized void start() throws Exception {
        initValidator();
        initServer();
        _server.start();
        _svcBeacon.start();
    }

    private void initValidator() {
        Validator.setCoordinator(_coordinator);
        Validator.setAuthSvcEndPointLocator(_authSvcEndPointLocator);
        Validator.setStorageOSUserRepository(_repository);
    }

    @Override
    public synchronized void stop() throws Exception {
        _server.stop();
        _dbClient.stop();
    }
}
