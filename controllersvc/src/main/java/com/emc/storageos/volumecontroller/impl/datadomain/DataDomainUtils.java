/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.datadomain;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.datadomain.restapi.DataDomainApiConstants;
import com.emc.storageos.datadomain.restapi.DataDomainClient;
import com.emc.storageos.datadomain.restapi.DataDomainClientFactory;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;

public class DataDomainUtils {

    private static final Logger _log = LoggerFactory.getLogger(DataDomainUtils.class);

    /**
     * Get DataDomain device represented by the StorageDevice
     * 
     * @param device StorageDevice object
     * @return DataDomainClient object
     * @throws com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException
     */
    private static DataDomainClient getDataDomainClient(StorageProvider provider,
            DataDomainClientFactory ddClientFactory) throws DataDomainApiException {
        DataDomainClient ddClient = null;
        if (provider != null) {
            ddClient = (DataDomainClient) ddClientFactory.getRESTClient(
                    DataDomainApiConstants.newDataDomainBaseURI(
                            provider.getIPAddress(),
                            provider.getPortNumber()),
                    provider.getUserName(),
                    provider.getPassword());
        }
        return ddClient;
    }

    /**
     * Refresh DataDomain connections.
     * 
     * @param ddProviderList the DataDomain provider list
     * @param dbClient the db client
     * @return the list of active providers
     */
    public static List<URI> refreshDDConnections(final List<StorageProvider> ddProviderList,
            DbClient dbClient, DataDomainClientFactory ddClientFactory) {
        List<URI> activeProviders = new ArrayList<URI>();
        for (StorageProvider storageProvider : ddProviderList) {
            try {
                // Is the DDMC reachable
                DataDomainClient ddClient = getDataDomainClient(storageProvider, ddClientFactory);
                if (ddClient == null) {
                    storageProvider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.name());
                    _log.error("Storage Provider {} is not reachable", storageProvider.getIPAddress());
                } else {
                    ddClient.getManagementSystemInfo();
                    storageProvider.setConnectionStatus(ConnectionStatus.CONNECTED.name());
                    activeProviders.add(storageProvider.getId());
                    _log.info("Storage Provider {} is reachable", storageProvider.getIPAddress());
                }
            } catch (Exception e) {
                storageProvider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.name());
                _log.error("Storage Provider {} is not reachable", storageProvider.getIPAddress());
            } finally {
                dbClient.persistObject(storageProvider);
            }
        }
        return activeProviders;
    }

}
