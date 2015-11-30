/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.scaleio;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.scaleio.ScaleIOException;
import com.emc.storageos.scaleio.api.ScaleIOConstants;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;

public class ScaleIOHandleFactory {

    private final Object syncObject = new Object();

    private DbClient dbClient;
    private ScaleIORestClientFactory scaleIORestClientFactory;

    public void setDbClient(DbClient client) {
        dbClient = client;
    }

    public ScaleIORestClientFactory getScaleIORestClientFactory() {
        return scaleIORestClientFactory;
    }

    public void setScaleIORestClientFactory(
            ScaleIORestClientFactory scaleIORestClientFactory) {
        this.scaleIORestClientFactory = scaleIORestClientFactory;
    }

    public ScaleIORestClient getClientHandle(StorageSystem storageSystem) throws Exception {
        ScaleIORestClient handle = null;
        synchronized (syncObject) {
            URI activeProviderURI = storageSystem.getActiveProviderURI();
            if (NullColumnValueGetter.isNullURI(activeProviderURI)) {
                throw ScaleIOException.exceptions.noActiveStorageProvider(storageSystem.getNativeGuid());
            }
            StorageProvider provider = dbClient.queryObject(StorageProvider.class, activeProviderURI);
            if (provider == null) {
                throw ScaleIOException.exceptions.noActiveStorageProvider(storageSystem.getNativeGuid());
            }
            handle = getHandle(handle, provider);
        }
        return handle;
    }

    public ScaleIORestClient getClientHandle(StorageProvider provider) throws Exception {
        ScaleIORestClient handle = null;
        synchronized (syncObject) {
            handle = getHandle(handle, provider);
        }
        return handle;
    }

    private ScaleIORestClient getHandle(ScaleIORestClient handle, StorageProvider provider) throws Exception {
        if (StorageProvider.InterfaceType.scaleioapi.name().equals(provider.getInterfaceType())) {
            if (handle == null) {
                URI baseURI = URI.create(ScaleIOConstants.getAPIBaseURI(provider.getIPAddress(), provider.getPortNumber()));
                handle = (ScaleIORestClient) scaleIORestClientFactory.getRESTClient(baseURI, provider.getUserName(),
                        provider.getPassword());
            }
        }
        return handle;
    }

    public ScaleIOHandleFactory using(DbClient dbClient) {
        setDbClient(dbClient);
        return this;
    }

}
