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
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.vmax.VMAXException;
import com.emc.storageos.vmax.restapi.VMAXApiClient;
import com.emc.storageos.vmax.restapi.VMAXApiRestClientFactory;
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
    private VMAXApiRestClientFactory clientFactory;

    public VMAXCommunicationInterface() {
    }

    public VMAXApiRestClientFactory getClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(VMAXApiRestClientFactory clientFactory) {
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
        StorageProvider.ConnectionStatus cxnStatus = StorageProvider.ConnectionStatus.CONNECTED;
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, accessProfile.getSystemId());

        _locker.acquireLock(accessProfile.getIpAddress(), LOCK_WAIT_SECONDS);
        try {
            VMAXApiClient apiClient = clientFactory.getClient(accessProfile.getIpAddress(), accessProfile.getPortNumber(),
                    Boolean.parseBoolean(accessProfile.getSslEnable()), accessProfile.getUserName(), accessProfile.getPassword());
            detectSystems(apiClient, provider);
        } catch (Exception e) {
            cxnStatus = StorageProvider.ConnectionStatus.NOTCONNECTED;
            logger.error(String.format("Exception was encountered when attempting to scan VMAX Instance %s",
                    accessProfile.getIpAddress()), e);
            throw VMAXException.exceptions.scanFailed(accessProfile.getIpAddress(), e);
        } finally {
            provider.setConnectionStatus(cxnStatus.name());
            _dbClient.updateObject(provider);
            logger.info("Completed scan of Unity StorageProvider. IP={}", accessProfile.getIpAddress());
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

    private void detectSystems(VMAXApiClient apiClient, StorageProvider provider) throws VMAXException {
        URI providerId = provider.getId();
        String version = apiClient.getApiVersion();
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
            Set<String> discoveredSystems = apiClient.getLocalStorageSystems();
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
        }
    }
}
