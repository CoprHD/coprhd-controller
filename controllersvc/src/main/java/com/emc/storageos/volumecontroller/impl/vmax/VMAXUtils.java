/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.vmax.restapi.VMAXApiClient;
import com.emc.storageos.vmax.restapi.VMAXApiClientFactory;
import com.emc.storageos.vmax.restapi.errorhandling.VMAXException;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationStorageGroupResponse;

public class VMAXUtils {
    private static final Logger logger = LoggerFactory.getLogger(VMAXUtils.class);

    private VMAXUtils() {
    }

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

    /**
     * Get Unisphere REST API client
     *
     * @param provider storage provider
     * @return clientFactory
     * @throws Exception
     */
    public static VMAXApiClient getApiClient(StorageProvider provider, VMAXApiClientFactory clientFactory) throws Exception {
        return clientFactory.getClient(provider.getIPAddress(), provider.getPortNumber(), provider.getUseSSL(), provider.getUserName(),
                provider.getPassword());
    }

    /**
     * Get Storage Provider
     *
     * Target system's REST provider will be used if both source and target have REST provider
     *
     * @param sourceSystem source storage system
     * @param targetSystem target storage system
     * @param dbClient DbClient
     */
    public static StorageProvider getRestProvider(StorageSystem sourceSystem, StorageSystem targetSystem, DbClient dbClient) {
        StorageProvider provider = null;
        try {
            provider = getRestProvider(targetSystem, dbClient);
        } catch (VMAXException targetEx) {
            try {
                // try source provider
                provider = getRestProvider(sourceSystem, dbClient);
            } catch (VMAXException sourceEx) {
                String msg = targetEx.getMessage() + " " + sourceEx.getMessage();
                logger.error(msg);
                throw DeviceControllerExceptions.vmax.providerUnreachable(msg);
            }
        }
        return provider;
    }

    private static StorageProvider getRestProvider(StorageSystem system, DbClient dbClient) {
        URI restProvider = system.getRestProvider();
        StringBuilder msg = new StringBuilder();

        if (NullColumnValueGetter.isNullURI(restProvider)) {
            msg.append("REST provider is not set for ").append(system.getNativeGuid());
        } else {
            StorageProvider provider = dbClient.queryObject(StorageProvider.class, restProvider);
            if (provider == null || provider.getInactive()) {
                msg.append("REST provider is not set for ").append(system.getNativeGuid());
            } else if (!provider.connected()) {
                msg.append("REST provider is not connected for ").append(system.getNativeGuid());
            } else {
                return provider;
            }
        }

        logger.warn(msg.toString());
        throw DeviceControllerExceptions.vmax.providerUnreachable(msg.toString());
    }

    /**
     * Updates percentage done based on the totalCapacity and remaining capacity information available in SG response
     * 
     * @param migrationURI
     * @param dbClient
     * @param sgResponse
     */
    public static void updatePercentageDone(URI migrationURI, DbClient dbClient, MigrationStorageGroupResponse sgResponse) {
        if (!NullColumnValueGetter.isNullURI(migrationURI) && sgResponse != null) {
            Migration migration = dbClient.queryObject(Migration.class, migrationURI);
            int percent = 0;
            if (sgResponse.getTotalCapacity() != 0) { // To avoid divide by zero error
                percent = (int) ((sgResponse.getTotalCapacity() - sgResponse.getRemainingCapacity()) / sgResponse.getTotalCapacity()) * 100;
            }
            logger.info("Percent done :{}%", percent);
            migration.setPercentDone(String.valueOf(percent));
            dbClient.updateObject(migration);
        }
    }

}
