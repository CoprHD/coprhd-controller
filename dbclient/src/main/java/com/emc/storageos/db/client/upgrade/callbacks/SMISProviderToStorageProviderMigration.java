/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.SMISProvider;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migrated SMISProvider CF instances to StorageProvider CF
 * 
 * @author anbals
 * 
 */
public class SMISProviderToStorageProviderMigration extends
        BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(SMISProviderToStorageProviderMigration.class);

    /**
     * 1. Create new StorageProvider instance per smis provider instance available in db.
     * 2. Populate all existing fields into new StorageProvider instance except id and interfaceType.
     * 3. Generate new id for the new instance. interfaceType will be "smis" in this case.
     * 4. Persist new instance into db.
     * 5: Update the newly created storage provider Id reference with the all storage systems managed by smis provider.
     * a) : Fetch storageSystems using provider.getStorageSystems()
     * b) : Iterate each storage systems.
     * c) : Needs to change storageSystem.activeProviderURI and storageSystem.getProviders() with the newly created
     * StorageProvider id. In this step we need to remove the existing smis provider id add new storage provider id
     */
    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();

        try {
            List<URI> smisProviderURIList = dbClient.queryByType(SMISProvider.class, true);
            Iterator<SMISProvider> smisProviderListIterator =
                    dbClient.queryIterativeObjects(SMISProvider.class, smisProviderURIList);
            while (smisProviderListIterator.hasNext()) {
                SMISProvider smisProvider = smisProviderListIterator.next();
                StorageProvider newStorageProvider = createNewStorageProviderInstance(smisProvider);
                dbClient.createObject(newStorageProvider);
                StringSet storageSystemSet = smisProvider.getStorageSystems();
                if (storageSystemSet != null) {
                    for (String strStorageSystem : storageSystemSet) {
                        URI storageSystemURI = URI.create(strStorageSystem);
                        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemURI);
                        updateStorageProvidersforStorageSystems(dbClient, storageSystem, smisProvider, newStorageProvider);
                        smisProvider.setInactive(true);
                        dbClient.persistObject(smisProvider);
                    }
                }
            }

            // Handle VPLEX storage systems, which are now discovered using the
            // StorageProvider model.
            List<URI> storageSystemURIs = dbClient.queryByType(StorageSystem.class, true);
            Iterator<StorageSystem> storageSystemIter = dbClient.queryIterativeObjects(
                    StorageSystem.class, storageSystemURIs);
            while (storageSystemIter.hasNext()) {
                StorageSystem storageSystem = storageSystemIter.next();
                if (DiscoveredDataObject.Type.vplex.name().equals(storageSystem.getSystemType())) {
                    createStorageProviderForVPlexSystem(storageSystem);
                }
            }
        } catch (Exception e) {
            log.error("Exception occured while migrating SMISProvider CF to StorageProvider");
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Replaces old smisProvider id with the newly created storageProvider id in StorageSystem CF
     * 
     * @param dbClient
     * @param storageSystem
     * @param smisProvider
     * @param storageProvider
     */
    private void updateStorageProvidersforStorageSystems(DbClient dbClient,
            StorageSystem storageSystem, SMISProvider smisProvider,
            StorageProvider storageProvider) {
        URI storageSystemActiveProviderURI = storageSystem.getActiveProviderURI();

        if (smisProvider.getId().toString().equals(storageSystemActiveProviderURI.toString())) {
            storageSystem.setActiveProviderURI(storageProvider.getId());
        }

        StringSet providers = storageSystem.getProviders();
        if (providers == null) {
            storageSystem.setProviders(new StringSet());
        }
        storageSystem.getProviders().remove(smisProvider.getId().toString());
        storageSystem.getProviders().add(storageProvider.getId().toString());
        dbClient.updateAndReindexObject(storageSystem);
    }

    /**
     * Creates new StorageProvider instance for the given smis provider while doing db upgrade.
     * 
     * @param smisProvider
     * @return {@link StorageProvider} newly created StorageProvider instance
     */
    private StorageProvider createNewStorageProviderInstance(SMISProvider smisProvider) {
        StorageProvider storageProvider = new StorageProvider();
        storageProvider.setId(URIUtil.createId(StorageProvider.class));

        storageProvider.setCompatibilityStatus(smisProvider.getCompatibilityStatus());
        storageProvider.setConnectionStatus(smisProvider.getConnectionStatus());
        storageProvider.setCreationTime(smisProvider.getCreationTime());
        storageProvider.setDecommissionedSystems(smisProvider.getDecommissionedSystems());
        storageProvider.setDescription(smisProvider.getDescription());
        storageProvider.setInterfaceType(StorageProvider.InterfaceType.smis.name());
        storageProvider.setIPAddress(smisProvider.getIPAddress());
        storageProvider.setLabel(smisProvider.getLabel());
        storageProvider.setLastScanStatusMessage(smisProvider.getLastScanStatusMessage());
        storageProvider.setLastScanTime(smisProvider.getLastScanTime());
        storageProvider.setManufacturer(smisProvider.getManufacturer());
        storageProvider.setNextScanTime(smisProvider.getNextScanTime());
        storageProvider.setPassword(smisProvider.getPassword());
        storageProvider.setPortNumber(smisProvider.getPortNumber());
        storageProvider.setProviderID(smisProvider.getProviderID());
        storageProvider.setRegistrationStatus(smisProvider.getRegistrationStatus());
        storageProvider.setScanStatus(smisProvider.getScanStatus());
        storageProvider.setOpStatus(smisProvider.getOpStatus());
        StringSet storageSystems = smisProvider.getStorageSystems();
        if (storageSystems != null) {
            StringSet newStoargeSystems = new StringSet();
            newStoargeSystems.addAll(storageSystems);
            storageProvider.setStorageSystems(newStoargeSystems);
        }

        storageProvider.setSuccessScanTime(smisProvider.getSuccessScanTime());
        storageProvider.setTag(smisProvider.getTag());
        storageProvider.setUserName(smisProvider.getUserName());
        storageProvider.setUseSSL(smisProvider.getUseSSL());
        storageProvider.setVersionString(smisProvider.getVersionString());
        return storageProvider;
    }

    /**
     * Creates a new storage provider representing the VPLEX management server
     * used to manage the pass VPLEX storage system. Also, updates the VPLEX
     * storage system itself to set the provider list and active provider to
     * this new storage provider.
     * 
     * @param vplexSystem An active VPLEX storage system in ViPR.
     */
    private void createStorageProviderForVPlexSystem(StorageSystem vplexSystem) {

        // We'll create a new storage provider representing the VPLEX management
        // server used to manage this VPLEX storage system.
        StorageProvider vplexMgmntSvr = new StorageProvider();
        URI vplexMgmntSvrURI = URIUtil.createId(StorageProvider.class);
        vplexMgmntSvr.setId(vplexMgmntSvrURI);
        vplexMgmntSvr.setInterfaceType(StorageProvider.InterfaceType.vplex.name());
        vplexMgmntSvr.setIPAddress(vplexSystem.getIpAddress());
        vplexMgmntSvr.setPortNumber(vplexSystem.getPortNumber());
        vplexMgmntSvr.setUseSSL(Boolean.TRUE);
        vplexMgmntSvr.setUserName(vplexSystem.getUsername());
        vplexMgmntSvr.setPassword(vplexSystem.getPassword());
        vplexMgmntSvr.setLabel(vplexSystem.getLabel());
        vplexMgmntSvr.setVersionString(vplexSystem.getFirmwareVersion());
        vplexMgmntSvr.setCompatibilityStatus(vplexSystem.getCompatibilityStatus());
        vplexMgmntSvr.setRegistrationStatus(vplexSystem.getRegistrationStatus());
        vplexMgmntSvr.setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED.name());
        StringSet managedStorageSystems = new StringSet();
        managedStorageSystems.add(vplexSystem.getId().toString());
        vplexMgmntSvr.setStorageSystems(managedStorageSystems);
        dbClient.createObject(vplexMgmntSvr);

        // Now update the providers and active provider for the VPLEX system.
        StringSet vplexMgmntServers = new StringSet();
        vplexMgmntServers.add(vplexMgmntSvrURI.toString());
        vplexSystem.setProviders(vplexMgmntServers);
        vplexSystem.setActiveProviderURI(vplexMgmntSvrURI);
        dbClient.updateAndReindexObject(vplexSystem);
    }
}
