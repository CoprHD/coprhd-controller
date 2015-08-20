/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexDeviceInfo;
import com.emc.storageos.vplex.api.VPlexResourceInfo;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

public class VPlexControllerUtils {
    // logger reference.
    private static final Logger log = LoggerFactory
            .getLogger(VPlexControllerUtils.class);

    /**
     * Get a DataObject. Throw exception if not found or inactive.
     * 
     * @param clazz
     * @param id
     * @return
     * @throws ControllerException
     */
    protected static <T extends DataObject> T getDataObject(Class<T> clazz, URI id, DbClient dbClient)
            throws DeviceControllerException {
        try {
            T object = null;

            if (id != null) {
                object = dbClient.queryObject(clazz, id);
                if (object == null) {
                    throw VPlexApiException.exceptions.getDataObjectFailedNotFound(
                            clazz.getSimpleName(), id.toString());
                }
                if (object.getInactive()) {
                    log.info("database object is inactive: " + object.getId().toString());
                    throw VPlexApiException.exceptions.getDataObjectFailedInactive(id.toString());
                }
            }
            return object;
        } catch (DatabaseException ex) {
            throw VPlexApiException.exceptions.getDataObjectFailedExc(id.toString(), ex);
        }
    }

    /**
     * Get the HTTP client for making requests to the passed VPlex management server.
     * 
     * @param vplexApiFactory A reference to the VPlex API factory.
     * @param vplexMnmgtSvr A VPlex management server.
     * @param dbClient A reference to a DB client.
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException
     */
    public static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            StorageProvider vplexMnmgtSvr, DbClient dbClient) throws URISyntaxException {
        URI vplexEndpointURI = new URI("https", null, vplexMnmgtSvr.getIPAddress(),
                vplexMnmgtSvr.getPortNumber(), "/", null, null);

        VPlexApiClient client = vplexApiFactory.getClient(vplexEndpointURI,
                vplexMnmgtSvr.getUserName(), vplexMnmgtSvr.getPassword());
        return client;
    }

    /**
     * Get the HTTP client for making requests to the passed VPlex storage system.
     * 
     * @param vplexApiFactory A reference to the VPlex API factory.
     * @param vplexSystem The VPlex storage system.
     * @param dbClient A reference to a DB client.
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException
     */
    public static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            StorageSystem vplexSystem, DbClient dbClient) throws URISyntaxException {
        // Create the URI to access the VPlex Management Server based
        // on the IP and port for the active provider for the passed
        // VPlex system.
        StorageProvider activeMgmntSvr = null;
        URI activeMgmntSvrURI = vplexSystem.getActiveProviderURI();
        if (!NullColumnValueGetter.isNullURI(activeMgmntSvrURI)) {
            activeMgmntSvr = dbClient.queryObject(StorageProvider.class, activeMgmntSvrURI);
        }

        if (activeMgmntSvr == null) {
            log.error("No active management server for VPLEX system {}", vplexSystem.getId());
            throw VPlexApiException.exceptions.connectionFailure(vplexSystem.getId().toString());
        }

        return getVPlexAPIClient(vplexApiFactory, activeMgmntSvr, dbClient);
    }

    /**
     * Get the HTTP client for making requests to the passed VPlex storage system URI.
     * Performs some helpful validation on the requested VPLEX system before returning
     * successfully.
     * 
     * @param vplexApiFactory A reference to the VPlex API factory.
     * @param vplexSystemUri The VPlex storage system URI.
     * @param dbClient A reference to a DB client.
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException
     */
    public static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            URI vplexUri, DbClient dbClient) throws URISyntaxException {

        if (vplexUri == null) {
            log.error("The provided VPLEX Storage System URI was null.");
            throw VPlexApiException.exceptions.vplexUriIsNull();
        }

        StorageSystem vplex = dbClient.queryObject(StorageSystem.class, vplexUri);

        if (vplex == null) {
            log.error("No VPLEX Storage System was found with URI {}.", vplexUri.toString());
            throw VPlexApiException.exceptions.vplexSystemNotFound(vplexUri.toString());
        }

        if (!vplex.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
            log.error("The Storage System (of type {}) with URI {} is not a VPLEX system.",
                    vplex.getSystemType(), vplexUri.toString());
            throw VPlexApiException.exceptions.invalidStorageSystemType(
                    vplex.getSystemType(), vplexUri.toString());
        }

        return getVPlexAPIClient(vplexApiFactory, vplex, dbClient);
    }

    /**
     * Determines the cluster name for a VPlex virtual volume based on the
     * volume's virtual array.
     * 
     * @param dbClient db client
     * @param vplexVolume The VPlex volume whose cluster we want to find.
     * @return The VPlex cluster name
     * @throws URISyntaxException
     */
    public static String getVPlexClusterName(DbClient dbClient, Volume vplexVolume) {
        String clusterName = null;
        // Get the virtual array from the vplex virtual volume. This will be used
        // to determine the volume's vplex cluster.
        URI vaURI = vplexVolume.getVirtualArray();
        // Get the volume's vplex storage system so we can a handle on the vplex client
        StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexVolume.getStorageController(), dbClient);
        VPlexApiClient client = null;

        try {
            client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), vplexSystem, dbClient);
        } catch (URISyntaxException e) {
            throw VPlexApiException.exceptions.connectionFailure(vplexVolume.getStorageController().toString());
        }

        StringSet assocVolumes = vplexVolume.getAssociatedVolumes();
        Iterator<String> assocVolumesIterator = assocVolumes.iterator();
        while (assocVolumesIterator.hasNext()) {
            Volume assocVolume = getDataObject(Volume.class,
                    URI.create(assocVolumesIterator.next()), dbClient);
            if (assocVolume.getVirtualArray().toString().equals(vaURI.toString())) {
                StorageSystem assocVolumeSystem = getDataObject(StorageSystem.class,
                        assocVolume.getStorageController(), dbClient);
                VolumeInfo info = new VolumeInfo(assocVolumeSystem.getNativeGuid(), assocVolumeSystem.getSystemType(),
                        assocVolume.getWWN().toUpperCase().replaceAll(":", ""),
                        assocVolume.getNativeId(), assocVolume.getThinlyProvisioned().booleanValue());
                clusterName = client.getClaimedStorageVolumeClusterName(info);
                log.info("Found cluster {} for volume", clusterName);
            }
        }

        return clusterName;
    }

    /**
     * Returns the cluster name (free form, user-configurable)
     * for a given VPLEX cluster id (either "1" or "2").
     * 
     * @param clusterId the cluster id (either "1" or "2")
     * @param vplexUri URI of the VPLEX to look at
     * @param dbClient a database client instance
     * 
     * @return the cluster name as configured by the user, or null
     *         if it couldn't be determined
     */
    public static String getClusterNameForId(String clusterId, URI vplexUri, DbClient dbClient) {

        String clusterName = null;
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            clusterName = client.getClusterName(clusterId);
        }

        log.info("VPLEX cluster name for cluster id {} is {}", clusterId, clusterName);
        return clusterName;
    }

    public static VPlexResourceInfo getSupportingDeviceInfo(String deviceName, String locality, 
            URI vplexUri, DbClient dbClient) throws VPlexApiException {
        VPlexResourceInfo device = null;
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            device = client.getDeviceStructure(deviceName, locality);
        }
        
        return device;
    }
    
    public static Set<String> getStorageVolumeInfoForDevice(String deviceName, String locality, 
            Map<String, Map<String, VPlexDeviceInfo>> mirrorMap, URI vplexUri, DbClient dbClient) {

        Set<String> storageVolumeInfo = null;
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            storageVolumeInfo = client.getStorageVolumeInfoForDevice(deviceName, locality, mirrorMap);
        }
        
        log.info("Backend storage volume wwns for {} are {}", deviceName, storageVolumeInfo);
        return storageVolumeInfo;
    }
    
    public static String getDeviceForStorageVolume(String volumeNativeId, 
            String wwn, String backendArraySerialNum, URI vplexUri, DbClient dbClient) {
        
        String deviceName = null;
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            deviceName = client.getDeviceForStorageVolume(volumeNativeId, wwn, backendArraySerialNum);
        }
        
        log.info("Device name for storage volume {} is {}", volumeNativeId, deviceName);
        return deviceName;
    }
    
    public static Map<String, String> getTopLevelDeviceMap(String deviceName, String locality, 
            URI vplexUri, DbClient dbClient) {
        
        Map <String, String> topLevelDeviceMap = null;
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            topLevelDeviceMap = client.getTopLevelDeviceMap(deviceName, locality);
        }
        
        log.info("Device map for device {} is {}", deviceName, topLevelDeviceMap);
        return topLevelDeviceMap;
    }
    
}
