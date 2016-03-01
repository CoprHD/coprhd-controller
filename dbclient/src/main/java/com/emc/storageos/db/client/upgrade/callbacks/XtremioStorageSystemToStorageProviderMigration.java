package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class XtremioStorageSystemToStorageProviderMigration
        extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(XtremioStorageSystemToStorageProviderMigration.class);

    /**
     * 1. Iterate each storage systems.
     * a) Create new StorageProvider instance per xtremio storage system instance available in db.
     * b) Update the newly created storage provider Id reference with the xtremio storage system using provider.setStorageSystems().
     * c) Need to change storageSystem.activeProviderURI and storageSystem.getProviders() with the newly created
     * StorageProvider id.
     */
    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();

        try {
            List<URI> storageSystemURIList = dbClient.queryByType(StorageSystem.class, true);
            List<StorageSystem> storageSystemsList = dbClient.queryObject(StorageSystem.class, storageSystemURIList);
            Iterator<StorageSystem> systemItr = storageSystemsList.iterator();
            List<StorageSystem> systemsToUpdate = new ArrayList<StorageSystem>();
            List<StorageProvider> storageProvidersToCreate = new ArrayList<StorageProvider>();
            while (systemItr.hasNext()) {
                StorageSystem storageSystem = systemItr.next();
                // perform storagesystem upgrade only for XtremIO storagesystems.
                if (DiscoveredDataObject.Type.xtremio.name().equalsIgnoreCase(storageSystem.getSystemType())) {
                    storageProvidersToCreate.add(createNewStorageProviderInstance(storageSystem));
                }
            }
            dbClient.createObject(storageProvidersToCreate);
            // persist all systems here.
            dbClient.persistObject(systemsToUpdate);
        } catch (Exception e) {
            log.error("Exception occured while updating xtremio storagesystem to storage provider model");
            log.error(e.getMessage(), e);
        }

    }

    /**
     * Creates new StorageProvider instance for the given storage system while doing db upgrade.
     * 
     * @param smisProvider
     * @return {@link StorageProvider} newly created StorageProvider instance
     */
    private StorageProvider createNewStorageProviderInstance(StorageSystem xioSystem) {
        log.info("Creating a new storage provider for storage system {}", xioSystem.getLabel());
        StorageProvider storageProvider = new StorageProvider();
        storageProvider.setId(URIUtil.createId(StorageProvider.class));

        storageProvider.setCompatibilityStatus(xioSystem.getCompatibilityStatus());
        // Set connectionStatus as Connected always, Let scan validate the connection later.
        storageProvider.setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED.name());
        storageProvider.setCreationTime(xioSystem.getCreationTime());
        storageProvider.setInterfaceType(StorageProvider.InterfaceType.xtremio.name());
        storageProvider.setIPAddress(xioSystem.getIpAddress());
        storageProvider.setLabel(xioSystem.getLabel());
        storageProvider.setLastScanStatusMessage(xioSystem.getLastDiscoveryStatusMessage());
        storageProvider.setLastScanTime(xioSystem.getLastDiscoveryRunTime());
        storageProvider.setPassword(xioSystem.getPassword());
        storageProvider.setPortNumber(xioSystem.getPortNumber());
        storageProvider.setRegistrationStatus(xioSystem.getRegistrationStatus());
        storageProvider.setScanStatus(xioSystem.getDiscoveryStatus());
        storageProvider.setOpStatus(xioSystem.getOpStatus());
        storageProvider.setSuccessScanTime(xioSystem.getSuccessDiscoveryTime());
        storageProvider.setTag(xioSystem.getTag());
        storageProvider.setUserName(xioSystem.getUsername());
        storageProvider.setVersionString(xioSystem.getFirmwareVersion());
        log.info("Adding the storage system to the storage provider");
        storageProvider.addStorageSystem(dbClient, xioSystem, true);
        return storageProvider;
    }

}
