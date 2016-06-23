/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.driver.vmaxv3driver.util.rest.RestClient;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;

/**
 * The base abstract implementation of "Operation" which is the super class of
 * all concrete "Operation" implementation class.
 *
 * Created by gang on 6/21/16.
 */
public abstract class OperationImpl implements Operation {

    private Registry registry;
    private LockManager lockManager;
    private RestClient client;

    /**
     * This method is used to create RestClient instance according to the given
     * StorageProvider input instance.
     *
     * @param storageProviderInput The given StorageProvider instance passed by SB SDK.
     */
    protected void setClient(StorageProvider storageProviderInput) {
        String scheme = storageProviderInput.getUseSSL() ? "https" : "http";
        String hostName = storageProviderInput.getProviderHost();
        int port = storageProviderInput.getPortNumber();
        String userName = storageProviderInput.getUsername();
        String password = storageProviderInput.getPassword();
        RestClient client = new RestClient(scheme, hostName, port, userName, password);
        this.setClient(client);
    }

    /**
     * This method is used to create RestClient instance according to the given
     * StorageSystem input instance.
     *
     * @param storageSystemInput The given StorageSystem instance passed by SB SDK.
     */
    protected void setClient(StorageSystem storageSystemInput) {
        String scheme = storageSystemInput.getProtocols().get(0);
        String hostName = storageSystemInput.getIpAddress();
        int port = storageSystemInput.getPortNumber();
        String userName = storageSystemInput.getUsername();
        String password = storageSystemInput.getPassword();
        RestClient client = new RestClient(scheme, hostName, port, userName, password);
        this.setClient(client);
    }

    public Registry getRegistry() {
        return registry;
    }

    @Override
    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public LockManager getLockManager() {
        return lockManager;
    }

    @Override
    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    public RestClient getClient() {
        return client;
    }

    public void setClient(RestClient client) {
        this.client = client;
    }
}
