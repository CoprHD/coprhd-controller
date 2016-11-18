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
        initStorageDriverManager();
        _server.start();
        _svcBeacon.start();
        // Launch OpenStack synchronization task if Keystone Authentication Provider exists.
        AuthnProvider keystoneProvider = _openStackSynchronizationTask.getKeystoneProvider();
        if (keystoneProvider != null && keystoneProvider.getAutoRegCoprHDNImportOSProjects()) {
             _openStackSynchronizationTask.start(_openStackSynchronizationTask.getTaskInterval(keystoneProvider));
        }
    }

    private void initStorageDriverManager() {
        ApplicationContext context = StorageDriverManager.getApplicationContext();
        if (context == null) {
            log.warn("ApplicationContext of StorageDriverManager instance is not set");
            return;
        }
        StorageDriverManager driverManager = (StorageDriverManager) context.getBean(StorageDriverManager.STORAGE_DRIVER_MANAGER);

        List<StorageSystemType> types = listNonNativeTypes();

        for (StorageSystemType type : types) {
            String typeName = type.getStorageTypeName();
            String driverName = type.getDriverName();
            if (type.getIsSmiProvider()) {
                driverManager.getStorageProvidersMap().put(driverName, typeName);
                continue;
            }
            driverManager.getStorageSystemsMap().put(driverName, typeName);
            if (type.getManagedBy() != null) {
                driverManager.getProviderManaged().add(typeName);
            } else {
                driverManager.getDirectlyManaged().add(typeName);
            }
            if (StringUtils.equals(type.getMetaType(), StorageSystemType.META_TYPE.FILE.toString())) {
                driverManager.getFileSystems().add(typeName);
            } else if (StringUtils.equals(type.getMetaType(), StorageSystemType.META_TYPE.BLOCK.toString())) {
                driverManager.getBlockSystems().add(typeName);
            }
            log.info("Driver info for storage system type {} has been set into storageDriverManager instancce", typeName);
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
            result.add(it.next());
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
