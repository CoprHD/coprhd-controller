/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.services.util.StorageDriverManager;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

/**
 * Utility class to capture common code and functionality
 */
public class ExternalDeviceUtils {

    private static final Logger _log = LoggerFactory.getLogger(ExternalDeviceUtils.class);
    private static StorageDriverManager driverManager;

    private static synchronized StorageDriverManager getDriverManager() {
        if (driverManager == null) {
            driverManager = (StorageDriverManager) ControllerServiceImpl.getBean(StorageDriverManager.STORAGE_DRIVER_MANAGER);
        }
        return driverManager;
    }
    /**
     * Updates the ViPR volume from the passed, newly created external device clone.
     * 
     * @param volume A reference to the volume representing the controller clone.
     * @param deviceClone A reference to the external device clone.
     * @param dbClient A reference to a database client.
     * 
     * @throws IOException When there is an issue generating the native GUID for the volume.
     */
    public static void updateNewlyCreatedClone(Volume volume, VolumeClone deviceClone, DbClient dbClient) throws IOException {
        volume.setNativeId(deviceClone.getNativeId());
        volume.setWWN(deviceClone.getWwn());
        volume.setDeviceLabel(deviceClone.getDeviceLabel());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setReplicaState(deviceClone.getReplicationState().name());
        volume.setProvisionedCapacity(deviceClone.getProvisionedCapacity());
        volume.setAllocatedCapacity(deviceClone.getAllocatedCapacity());
        volume.setInactive(false);
        dbClient.updateObject(volume);
    }

    /**
     * Updates the ViPR consistency group volume from the passed, newly created external
     * device group clone.
     * 
     * @param volume A reference to the volume representing the controller clone.
     * @param deviceClone A reference to the external device clone.
     * @param cgURI The URI of the consistency group.
     * @param dbClient A reference to a database client.
     * 
     * @throws IOException When there is an issue generating the native GUID for the volume.
     */
    public static void updateNewlyCreatedGroupClone(Volume volume, VolumeClone deviceClone, URI cgURI, DbClient dbClient) throws IOException {
        volume.setNativeId(deviceClone.getNativeId());
        volume.setWWN(deviceClone.getWwn());
        volume.setDeviceLabel(deviceClone.getDeviceLabel());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setReplicaState(deviceClone.getReplicationState().name());
        volume.setReplicationGroupInstance(deviceClone.getConsistencyGroup());
        volume.setProvisionedCapacity(deviceClone.getProvisionedCapacity());
        volume.setAllocatedCapacity(deviceClone.getAllocatedCapacity());
        volume.setInactive(false);
        volume.setConsistencyGroup(cgURI);
    }

    /**
     * Updates the ViPR volume from the passed, newly expanded external device volume to reflect
     * the expanded capacity.
     * 
     * @param volume A reference to the controller volume.
     * @param deviceVolume A reference to the external device volume.
     * @param dbClient A reference to a database client.
     */
    public static void updateExpandedVolume(Volume volume, StorageVolume deviceVolume, DbClient dbClient) {
        volume.setCapacity(deviceVolume.getRequestedCapacity());
        volume.setProvisionedCapacity(deviceVolume.getProvisionedCapacity());
        volume.setAllocatedCapacity(deviceVolume.getAllocatedCapacity());
        dbClient.updateObject(volume);
    }
    
    /**
     * Updates the ViPR volume from the passed external device clone after the clone is
     * successfully restored.
     * 
     * @param volume A reference to the Volume representing the controller clone..
     * @param deviceClone A reference to the external device clone.
     * @param dbClient A reference to a database client.
     * @param updateDb true to update the object in the DB, false otherwise.
     */
    public static void updateRestoredClone(Volume volume, VolumeClone deviceClone, DbClient dbClient, boolean updateDb) {
        volume.setReplicaState(deviceClone.getReplicationState().name());
        if (updateDb) {
            dbClient.updateObject(volume);
        }
    }
    
    /**
     * Determines if the passed volume (representing a controller clone) represents the
     * passed external device clone.
     *  
     * @param volume A reference o a controller clone.
     * @param deviceClone A reference to an external device clone.
     * @param dbClient A reference to a database client.
     * 
     * @return true if the controller volume represents the passed external device clone, false otherwise.
     */
    public static boolean isVolumeExternalDeviceClone(Volume volume, VolumeClone deviceClone, DbClient dbClient) {
        // Get the native id of the associated source volume for the controller clone.
        URI assocSourceVolumeURI = volume.getAssociatedSourceVolume();
        Volume assocSourceVolume = dbClient.queryObject(Volume.class, assocSourceVolumeURI);
        String assocSourceVolumeNativeId = assocSourceVolume.getNativeId();
        
        // The passed controller clone represents the passed external device clone if 
        // the native id of the associated source volume for the controller clone is
        // the parent id of the passed device clone.
        return deviceClone.getParentId().equals(assocSourceVolumeNativeId);
    }

    public static void updateStoragePoolCapacityAfterOperationComplete(URI storagePoolURI, URI storageSystemURI,
                                                                 List<URI> volumeURIs,  DbClient dbClient) {
        StoragePool dbPool = dbClient.queryObject(StoragePool.class, storagePoolURI);
        StorageSystem dbSystem = dbClient.queryObject(StorageSystem.class, storageSystemURI);
        ExternalBlockStorageDevice.updateStoragePoolCapacity(dbPool, dbSystem,
                volumeURIs, dbClient);
    }

    /**
     * Refresh connections to driver managed providers.
     *
     * @param dbClient db client
     * @return list of connected providers
     */
    public static List<URI> refreshProviderConnections(DbClient dbClient) {
        List<StorageProvider> externalProviders = new ArrayList<>();
        List<URI> externalProvidersUris = new ArrayList<>();

        try {
            StorageDriverManager driverManager = getDriverManager();
            Collection<String> externalDeviceProviderTypes = driverManager.getStorageProvidersMap().values();
            _log.info("Processing external provider types: {}", externalDeviceProviderTypes);

            String simulatorProviderType = driverManager.getStorageProvidersMap().get(StorageDriverManager.SIMULATOR);
            for (String providerType : externalDeviceProviderTypes) {
                if (!providerType.equals(simulatorProviderType)) {
                    // skip simulator provider
                    externalProviders.addAll(CustomQueryUtility.getActiveStorageProvidersByInterfaceType(
                            dbClient, providerType));
                }
            }
        } catch (Exception e) {
            _log.error("Failed to refresh connections for external providers.", e);
            return externalProvidersUris;
        }

        for (StorageProvider storageProvider : externalProviders) {
            try {
                // Makes sure external provider is reachable
                checkProviderConnection(storageProvider);
                storageProvider.setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED.name());
                externalProvidersUris.add(storageProvider.getId());
                _log.info("Storage Provider {}/{} is reachable", storageProvider.getLabel(), storageProvider.getIPAddress());
            } catch (Exception e) {
                storageProvider.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED.name());
                _log.error("Storage Provider {}/{} is not reachable", storageProvider.getLabel(), storageProvider.getIPAddress(), e);
            } finally {
                dbClient.updateObject(storageProvider);
            }
        }
        return externalProvidersUris;
    }


    /**
     * Check connection to external provider by opening sftp session to it.
     *
     * @param storageProvider storage provider
     * @throws JSchException
     * @throws SftpException
     * @throws IOException
     */
    private static void checkProviderConnection(StorageProvider storageProvider)
            throws JSchException, SftpException, IOException {

        // Adopted from CinderUtils.
        final Integer timeout = 10000;           // in milliseconds
        final Integer connectTimeout = 10000;    // in milliseconds
        final String STRICT_HOST_KEY_CHECKING = "StrictHostKeyChecking";
        final String NO = "no";
        ChannelSftp sftp = null;
        Session session = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(storageProvider.getUserName(),
                    storageProvider.getIPAddress(),
                    storageProvider.getPortNumber());
            session.setPassword(storageProvider.getPassword());
            Hashtable<String, String> config = new Hashtable<String, String>();
            config.put(STRICT_HOST_KEY_CHECKING, NO);
            session.setConfig(config);
            session.connect(timeout);
            _log.debug("Session Connected...");
            Channel channel = session.openChannel("sftp");
            sftp = (ChannelSftp) channel;
            sftp.connect(connectTimeout);
        } finally {
            if (sftp != null) {
                sftp.disconnect(); // disconnect will automatically close channel
            }
            if (session != null) {
                session.disconnect(); // disconnect will automatically close session
            }
        }
    }
}
