/*
 * Copyright (c) 2017 DELL EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.vmax.restapi.VMAXApiClient;
import com.emc.storageos.vmax.restapi.VMAXApiClientFactory;

public class VMAXUtils {
    private static final Logger logger = LoggerFactory.getLogger(VMAXUtils.class);

    /**
     * Refresh Unisphere REST client connections
     *
     * @param providerList providers
     * @param dbClient
     * @return the list of active providers
     */
    public static List<URI> refreshConnections(final List<StorageProvider> providerList,
            DbClient dbClient, VMAXApiClientFactory clientFactory) {
        List<URI> activeProviders = new ArrayList<>();
        for (StorageProvider provider : providerList) {
            VMAXApiClient apiClient = null;
            try {
                apiClient = clientFactory.getClient(provider.getIPAddress(), provider.getPortNumber(), provider.getUseSSL(),
                        provider.getUserName(), provider.getPassword());
                if (apiClient.getApiVersion() != null) {
                    // update provider status based on connection live check
                    provider.setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED.toString());
                    activeProviders.add(provider.getId());
                } else {
                    logger.info("Unisphere connection is not active {}", provider.getProviderID());
                    provider.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED
                            .toString());
                }
            } catch (Exception ex) {
                logger.error("Exception occurred while validating Unisphere REST API client for {}", provider.getProviderID(), ex);
                provider.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED.toString());
            } finally {
                dbClient.updateObject(provider);
                if (apiClient != null) {
                    apiClient.close();
                }
            }
        }

        return activeProviders;
    }
}
