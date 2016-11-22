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
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.security.AbstractSecuredWebServer;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.authentication.StorageOSUserRepository;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.services.util.StorageDriverManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Provisioning service default implementation
 */
public class ProvisioningServiceImpl extends AbstractSecuredWebServer implements ProvisioningService {
    private static final Logger log = LoggerFactory.getLogger(ProvisioningServiceImpl.class);

    @Autowired
    private CoordinatorClient _coordinator;

    @Autowired
    private AuthSvcEndPointLocator _authSvcEndPointLocator;

    @Autowired
    StorageOSUserRepository _repository;

    @Autowired
    ServiceBeacon _svcBeacon;

    private OpenStackSynchronizationTask _openStackSynchronizationTask;

    public void setOpenStackSynchronizationTask(OpenStackSynchronizationTask openStackSynchronizationTask) {
        this._openStackSynchronizationTask = openStackSynchronizationTask;
    }

    @Override
    public synchronized void start() throws Exception {
        initValidator();
        initServer();
        _server.start();
        _svcBeacon.start();
        // Launch OpenStack synchronization task if Keystone Authentication Provider exists.
        AuthnProvider keystoneProvider = _openStackSynchronizationTask.getKeystoneProvider();
        if (keystoneProvider != null && keystoneProvider.getAutoRegCoprHDNImportOSProjects()) {
             _openStackSynchronizationTask.start(_openStackSynchronizationTask.getTaskInterval(keystoneProvider));
        }
    }

    private List<StorageSystemType> listNonNativeTypes() {
        List<StorageSystemType> result = new ArrayList<StorageSystemType>();
        List<URI> ids = _dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = _dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (it.hasNext()) {
            StorageSystemType type = it.next();
            if (type.getIsNative() == null ||type.getIsNative() == true) {
                continue;
            }
            if (StringUtils.equals(type.getDriverStatus(), StorageSystemType.STATUS.ACTIVE.toString())) {
                result.add(it.next());
            }
        }
        return result;
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
        AuthnProvider keystoneProvider = _openStackSynchronizationTask.getKeystoneProvider();
        if (keystoneProvider != null && keystoneProvider.getAutoRegCoprHDNImportOSProjects()) {
            _openStackSynchronizationTask.stop();
        }
    }
}
