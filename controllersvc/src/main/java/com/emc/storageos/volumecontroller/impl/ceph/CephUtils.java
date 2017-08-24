/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.ceph;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.ceph.CephClient;
import com.emc.storageos.ceph.CephClientFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;

/**
 * Utils for Ceph related operations
 *
 */
public final class CephUtils {

    private static final Logger _log = LoggerFactory.getLogger(CephUtils.class);

    private CephUtils() {
    }

    public static CephClient connectToCeph(CephClientFactory factory, StorageSystem storage) {
        return factory.getClient(storage.getSmisProviderIP(), storage.getSmisUserName(), storage.getSmisPassword());
    }

    public static CephClient connectToCeph(CephClientFactory factory, StorageProvider provider) {
        return factory.getClient(provider.getIPAddress(), provider.getUserName(), provider.getPassword());
    }

    private static void ensureConnectToCeph(StorageProvider storageProvider) throws Exception {
        CephClientFactory factory = new CephClientFactory();
        CephClient cephClient = factory.getClient(storageProvider.getIPAddress(),
                storageProvider.getUserName(), storageProvider.getPassword());
        cephClient.close();
    }

    public static String createNativeId(DataObject object) {
        return String.format("%s:%s", object.getLabel(), URIUtil.parseUUIDFromURI(object.getId()));
    }

    public static List<URI> refreshCephConnections(final List<StorageProvider> cephProviderList,
            DbClient dbClient) {
        List<URI> activeProviders = new ArrayList<URI>();
        for (StorageProvider storageProvider : cephProviderList) {
            try {
                ensureConnectToCeph(storageProvider);
                storageProvider.setConnectionStatus(ConnectionStatus.CONNECTED.name());
                activeProviders.add(storageProvider.getId());
            } catch (Exception e) {
                _log.error(String.format("Failed to connect to Ceph %s: %s",
                        storageProvider.getIPAddress(), storageProvider.getId()), e);
                storageProvider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.name());
            } finally {
                dbClient.updateObject(storageProvider);
            }
        }
        return activeProviders;
    }
}
