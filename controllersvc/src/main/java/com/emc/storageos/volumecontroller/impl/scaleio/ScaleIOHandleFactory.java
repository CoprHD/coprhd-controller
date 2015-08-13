/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.scaleio;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.scaleio.api.ScaleIOContants;
import com.emc.storageos.scaleio.api.ScaleIOHandle;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScaleIOHandleFactory {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOHandleFactory.class);
    private final Map<String, ScaleIOHandle> scaleIOHandleMap = new ConcurrentHashMap<String, ScaleIOHandle>();
    private final Map<ScaleIOHandle, String> scaleIOHandleVersionMap =
            new ConcurrentHashMap<>();
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

    public ScaleIOHandle getClientHandle(StorageSystem storageSystem) throws Exception {
        ScaleIOHandle handle = null;
        synchronized (syncObject) {
            URI activeProviderURI = storageSystem.getActiveProviderURI();
            StorageProvider provider = dbClient.queryObject(StorageProvider.class, activeProviderURI);
            String providerId = provider.getProviderID();
            handle = scaleIOHandleMap.get(providerId);
            handle = getHandle(handle, provider);
        }
        return handle;
    }

    public ScaleIOHandle getClientHandle(StorageProvider provider) throws Exception {
        ScaleIOHandle handle = null;
        synchronized (syncObject) {
            String providerId = provider.getProviderID();
            handle = scaleIOHandleMap.get(providerId);
            handle = getHandle(handle, provider);
        }
        return handle;
    }

    private ScaleIOHandle getHandle(ScaleIOHandle handle, StorageProvider provider) throws Exception {
        if (StorageProvider.InterfaceType.scaleioapi.name().equals(provider.getInterfaceType())) {
            if (handle == null) {
                URI baseURI = URI.create(ScaleIOContants.getAPIBaseURI(provider.getIPAddress(), provider.getPortNumber()));
                handle = (ScaleIORestClient) scaleIORestClientFactory.getRESTClient(baseURI, provider.getUserName(),
                        provider.getPassword(), true);
                ScaleIORestClient client = (ScaleIORestClient) handle;
                String version = client.init();
                scaleIOHandleMap.put(provider.getProviderID(), client);
                scaleIOHandleVersionMap.put(client, version);
            }
            ScaleIORestClient client = (ScaleIORestClient) handle;
            if (!Strings.isNullOrEmpty(provider.getUserName())) {
                client.setUsername(provider.getUserName());
            }
            if (!Strings.isNullOrEmpty(provider.getPassword())) {
                client.setPassword(provider.getPassword());
            }
        } else {
            log.info("The storage provider interface type is not supported: %s ", provider.getInterfaceType());
            //not supported
            handle = null;
        }
        return handle;
    }

    public ScaleIOHandleFactory using(DbClient dbClient) {
        setDbClient(dbClient);
        return this;
    }

   
}
