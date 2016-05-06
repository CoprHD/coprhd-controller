/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl;

import com.emc.storageos.api.service.ProvisioningService;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.api.service.impl.resource.utils.OpenStackSynchronizationTask;
import com.emc.storageos.db.client.model.AuthnProvider;
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

    private OpenStackSynchronizationTask _openStackSynchronizationTask;

    public void setOpenStackSynchronizationTask(OpenStackSynchronizationTask _openStackSynchronizationTask) {
        this._openStackSynchronizationTask = _openStackSynchronizationTask;
    }

    @Override
    public synchronized void start() throws Exception {
        initValidator();
        initServer();
        _server.start();
        _svcBeacon.start();
        // Launch OpenStack synchronization task if Keystone Authentication Provider exists.
        AuthnProvider keystoneProvider = _openStackSynchronizationTask.getKeystoneProvider();
        if (keystoneProvider != null) {
             _openStackSynchronizationTask.start(_openStackSynchronizationTask.getTaskInterval());
        }
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
        if (_openStackSynchronizationTask.doesKeystoneProviderExist()){
            _openStackSynchronizationTask.stop();
        }
    }
}
