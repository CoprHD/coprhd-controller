/*
 * Copyright (c) 2017 DELL EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.vmax.restapi.VMAXApiClient;
import com.emc.storageos.vmax.restapi.VMAXApiClientFactory;
import com.emc.storageos.vmax.restapi.errorhandling.VMAXException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * VMAXCommunicationInterface class is an implementation of
 * CommunicationInterface which is responsible to detect VMAX storage systems using Unisphere REST API
 */
public class VMAXCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private static final Logger logger = LoggerFactory.getLogger(VMAXCommunicationInterface.class);
    private static final int LOCK_WAIT_SECONDS = 300;
    private static final String UNISPHERE_VERSION = "controller_unisphere_provider_version";

    // Reference to the VMAX unisphere client factory allows us to get a VMAX unisphere
    // client and execute requests to the VMAX storage system.
    private VMAXApiClientFactory clientFactory;

    public VMAXCommunicationInterface() {
    }

    public VMAXApiClientFactory getClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(VMAXApiClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * Implementation for scan for VMAX storage systems
     *
     * @param accessProfile
     *
     * @throws BaseCollectionException
     */
    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        logger.info("Starting scan of Unity StorageProvider. IP={}", accessProfile.getIpAddress());
        StorageProvider provider = null;
        String detailedStatusMessage = "Unknown Status";
        StorageProvider.ConnectionStatus cxnStatus = StorageProvider.ConnectionStatus.CONNECTED;
        _locker.acquireLock(accessProfile.getIpAddress(), LOCK_WAIT_SECONDS);
        VMAXApiClient apiClient = null;

        try {
            provider = _dbClient.queryObject(StorageProvider.class, accessProfile.getSystemId());
            apiClient = clientFactory.getClient(accessProfile.getIpAddress(), accessProfile.getPortNumber(),
                    Boolean.parseBoolean(accessProfile.getSslEnable()), accessProfile.getUserName(), accessProfile.getPassword());
            detectSystems(apiClient, provider);
            // scan succeeds
            detailedStatusMessage = String.format("Scan job completed successfully for " +
                    "Unisphere REST API provider: %s", accessProfile.getIpAddress());
        } catch (Exception e) {
            detailedStatusMessage = String.format("Scan job failed for Unisphere REST API provider: %s because %s",
                    accessProfile.getIpAddress(), e.getMessage());
            logger.error(detailedStatusMessage, e);
            cxnStatus = StorageProvider.ConnectionStatus.NOTCONNECTED;
            throw VMAXException.exceptions.scanFailed(accessProfile.getIpAddress(), e);
        } finally {
            if (provider != null) {
                try {
                    // set detailed message
                    provider.setLastScanStatusMessage(detailedStatusMessage);
                    provider.setConnectionStatus(cxnStatus.name());
                    _dbClient.updateObject(provider);
                } catch (DatabaseException ex) {
                    logger.error("Error while persisting object to DB", ex);
                }
            }

            if (apiClient != null) {
                apiClient.close();
            }

            logger.info("Completed scan with Unity REST API. IP={}", accessProfile.getIpAddress());
            _locker.releaseLock(accessProfile.getIpAddress());
        }
    }

    /**
     * Discovery of VMAX storage systems should be performed via SMI-S
     *
     * @param accessProfile
     *
     * @throws VMAXException
     */
    @Override
    public void discover(AccessProfile accessProfile) throws VMAXException {
        logger.info("Discovery using Unishpere REST API is not supported. Access Profile Details :  IpAddress : {}, PortNumber : {}",
                accessProfile.getIpAddress(),
                accessProfile.getPortNumber());
        throw VMAXException.exceptions.discoveryNotSupported();
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile) throws BaseCollectionException {
        return;
    }

    /**
     * Detect local storage systems
     *
     * @param apiClient
     * @param provider
     * @throws Exception
     */
    private void detectSystems(VMAXApiClient apiClient, StorageProvider provider) throws Exception {
        URI providerId = provider.getId();
        String version = "";

        try {
            version = apiClient.getApiVersion();
        } catch (Exception e) {
            logger.error("Exception on get Unishpere REST API version", e);
            provider.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.toString());
            _dbClient.updateObject(provider);
            throw VMAXException.exceptions.scanFailed(provider.getIPAddress(), e);
        }

        // Get supported version from Coordinator
        String minimumSupportedVersion = ControllerUtils.getPropertyValueFromCoordinator(_coordinator, UNISPHERE_VERSION);
        logger.info("Verifying version details : Minimum Supported Version {} - Discovered Unisphere REST API Version {}",
                minimumSupportedVersion, version);
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) < 0) {
            String msg = String.format("Unisphere REST API version %s is not supported. Should be a minimum of %s",
                    version, minimumSupportedVersion);
            logger.warn(msg);
            provider.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.toString());
            _dbClient.updateObject(provider);
            throw VMAXException.exceptions.unsupportedVersion(version, minimumSupportedVersion);
        } else {
            provider.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.toString());
            _dbClient.updateObject(provider);
        }

        // get all storage systems in DB, and update their REST API provider URI
        try {
            Set<String> discoveredSystems = apiClient.getLocalSystems();
            List<URI> storageSystemURIList = _dbClient.queryByType(StorageSystem.class, true);
            List<StorageSystem> storageSystemsList = _dbClient.queryObject(StorageSystem.class, storageSystemURIList);
            Iterator<StorageSystem> systemItr = storageSystemsList.iterator();
            List<StorageSystem> systemsToUpdate = new ArrayList<StorageSystem>();
            while (systemItr.hasNext()) {
                StorageSystem storageSystem = systemItr.next();
                if (DiscoveredDataObject.Type.vmax.name().equalsIgnoreCase(storageSystem.getSystemType())) {
                    URI existingProvider = storageSystem.getRestProvider();
                    boolean alreadyManaged = !NullColumnValueGetter.isNullURI(existingProvider) && existingProvider.equals(providerId);
                    boolean discovered = discoveredSystems.contains(storageSystem.getSerialNumber());
                    if (!alreadyManaged && discovered) {
                        storageSystem.setRestProvider(providerId);
                        systemsToUpdate.add(storageSystem);
                    } else if (alreadyManaged && !discovered) {
                        storageSystem.setRestProvider(NullColumnValueGetter.getNullURI());
                        systemsToUpdate.add(storageSystem);
                    }
                }
            }

            if (!systemsToUpdate.isEmpty()) {
                _dbClient.updateObject(systemsToUpdate);
            }
        } catch (Exception e) {
            logger.error("Exception occured while finding storage systems", e);
            throw e;
        }
    }
}
