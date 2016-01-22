/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio.prov.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.XtremIOConstants.XTREMIO_ENTITY_TYPE;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTag;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;

public class XtremIOProvUtils {

    private static final Logger _log = LoggerFactory.getLogger(XtremIOProvUtils.class);

    private static final int SLEEP_TIME = 10000; // 10 seconds

    private static final String DOT_OPERATOR = "\\.";
    private static final Integer XIO_MIN_4X_VERSION = 4;

    public static void updateStoragePoolCapacity(XtremIOClient client, DbClient dbClient, StoragePool storagePool) {
        try {
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storagePool.getStorageDevice());
            _log.info(String.format("Old storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));
            List<XtremIOSystem> systems = client.getXtremIOSystemInfo();
            for (XtremIOSystem system : systems) {
                if (system.getSerialNumber().equalsIgnoreCase(storageSystem.getSerialNumber())) {
                    storagePool.setFreeCapacity(system.getTotalCapacity() - system.getUsedCapacity());
                    dbClient.persistObject(storagePool);
                    break;
                }
            }
            _log.info(String.format("New storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));
        } catch (Exception e) {
            _log.warn("Problem when updating pool capacity for pool {}", storagePool.getNativeGuid());
        }
    }

    /**
     * Check if there is a volume with the given name
     * If found, return the volume
     * 
     * @param client
     * @param label
     * @param clusterName
     * @return XtremIO volume if found else null
     */
    public static XtremIOVolume isVolumeAvailableInArray(XtremIOClient client, String label, String clusterName) {
        XtremIOVolume volume = null;
        try {
            volume = client.getVolumeDetails(label, clusterName);
        } catch (Exception e) {
            _log.info("Volume {} already deleted.", label);
        }
        return volume;
    }

    /**
     * Check if there is a snapshot with the given name
     * If found, return the snapshot
     * 
     * @param client
     * @param label
     * @param clusterName
     * @return XtremIO snapshot if found else null
     */
    public static XtremIOVolume isSnapAvailableInArray(XtremIOClient client, String label, String clusterName) {
        XtremIOVolume volume = null;
        try {
            volume = client.getSnapShotDetails(label, clusterName);
        } catch (Exception e) {
            _log.info("Snapshot {} not available in Array.", label);
        }
        return volume;
    }

    /**
     * Check if there is a consistency group with the given name
     * If found, return the consistency group
     * 
     * @param client
     * @param label
     * @param clusterName
     * @return XtremIO consistency group if found else null
     */
    public static XtremIOConsistencyGroup isCGAvailableInArray(XtremIOClient client, String label, String clusterName) {
        XtremIOConsistencyGroup cg = null;
        try {
            cg = client.getConsistencyGroupDetails(label, clusterName);
        } catch (Exception e) {
            _log.info("Consistency group {} not available in Array.", label);
        }

        return cg;
    }

    /**
     * Check if there is a tag with the given name
     * If found, return the tag
     * 
     * @param client
     * @param tagName
     * @param tagEntityType
     * @param clusterName
     * @return XtrmIO tag if found else null
     */
    public static XtremIOTag isTagAvailableInArray(XtremIOClient client, String tagName, String tagEntityType, String clusterName){
        XtremIOTag tag = null;
        
        try{
            tag = client.getTagDetails(tagName, tagEntityType, clusterName);
        } catch (Exception e) {
            _log.info("Tag {} not available in Array.", tagName);
        }

        return tag;
    }

    /**
     * Check if there is a snapset with the given name
     * If found, return the snapset
     * 
     * @param client
     * @param label
     * @param clusterName
     * @return XtremIO snapset if found else null
     */
    public static XtremIOConsistencyGroup isSnapsetAvailableInArray(XtremIOClient client, String label, String clusterName) {
        XtremIOConsistencyGroup cg = null;
        try {
            cg = client.getSnapshotSetDetails(label, clusterName);
        } catch (Exception e) {
            _log.info("Snapshot Set {} not available in Array.", label);
        }

        return cg;
    }

    /**
     * Checks if there is folder with the given name and sub folders for volume and snapshots.
     * If not found, create them.
     * 
     * @param client
     * @param rootVolumeFolderName
     * @return map of volume folder name and snapshot folder name
     * @throws Exception
     */
    public static Map<String, String> createFoldersForVolumeAndSnaps(XtremIOClient client, String rootVolumeFolderName)
            throws Exception {

        List<String> folderNames = client.getVolumeFolderNames();
        _log.info("Volume folder Names found on Array : {}", Joiner.on("; ").join(folderNames));
        Map<String, String> folderNamesMap = new HashMap<String, String>();
        String rootFolderName = XtremIOConstants.V1_ROOT_FOLDER.concat(rootVolumeFolderName);
        _log.info("rootVolumeFolderName: {}", rootFolderName);
        String volumesFolderName = rootFolderName.concat(XtremIOConstants.VOLUMES_SUBFOLDER);
        String snapshotsFolderName = rootFolderName.concat(XtremIOConstants.SNAPSHOTS_SUBFOLDER);
        folderNamesMap.put(XtremIOConstants.VOLUME_KEY, volumesFolderName);
        folderNamesMap.put(XtremIOConstants.SNAPSHOT_KEY, snapshotsFolderName);
        if (!folderNames.contains(rootFolderName)) {
            _log.info("Sending create root folder request {}", rootFolderName);
            client.createTag(rootVolumeFolderName, "/", XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), null);
        } else {
            _log.info("Found {} folder on the Array.", rootFolderName);
        }
        long waitTime = 30000; // 30 sec
        int count = 0;
        // @TODO this is a temporary workaround to retry volume folder verification.
        // Actually we should create workflow steps for this.
        while (waitTime > 0) {
            count++;
            _log.debug("Retrying {} time to find the volume folders", count);
            if (!folderNames.contains(volumesFolderName)) {
                _log.debug("sleeping time {} remaining time: {}", SLEEP_TIME, (waitTime - SLEEP_TIME));
                Thread.sleep(SLEEP_TIME);
                waitTime = waitTime - SLEEP_TIME;
                folderNames = client.getVolumeFolderNames();
            } else {
                _log.info("Found {} folder on the Array.", volumesFolderName);
                break;
            }
        }
        if (!folderNames.contains(volumesFolderName)) {
            _log.info("Sending create volume folder request {}", volumesFolderName);
            client.createTag("volumes", rootFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), null);
        }

        if (!folderNames.contains(snapshotsFolderName)) {
            _log.info("Sending create snapshot folder request {}", snapshotsFolderName);
            client.createTag("snapshots", rootFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), null);
        } else {
            _log.info("Found {} folder on the Array.", snapshotsFolderName);
        }

        return folderNamesMap;
    }

    /**
     * Checks if there are tags with the given name for volume and snapshots.
     * If not found, create them.
     * 
     * @param client
     * @param rootTagName
     * @param clusterName
     * @return map of volume tag name and snapshot tag name
     * @throws Exception
     */
    public static Map<String, String> createTagsForVolumeAndSnaps(XtremIOClient client, String rootTagName, String clusterName)
            throws Exception {
        List<String> tagNames = client.getTagNames(clusterName);
        _log.info("Tag Names found on Array : {}", Joiner.on("; ").join(tagNames));
        Map<String, String> tagNamesMap = new HashMap<String, String>();
        String volumesTagName = XtremIOConstants.V2_VOLUME_ROOT_FOLDER.concat(rootTagName);
        String snapshotsTagName = XtremIOConstants.V2_SNAPSHOT_ROOT_FOLDER.concat(rootTagName);
        tagNamesMap.put(XtremIOConstants.VOLUME_KEY, volumesTagName);
        tagNamesMap.put(XtremIOConstants.SNAPSHOT_KEY, snapshotsTagName);
        long waitTime = 30000; // 30 sec
        int count = 0;
        // @TODO this is a temporary workaround to retry volume tag verification.
        // Actually we should create workflow steps for this.
        while (waitTime > 0) {
            count++;
            _log.debug("Retrying {} time to find the volume tag", count);
            if (!tagNames.contains(volumesTagName)) {
                _log.debug("sleeping time {} remaining time: {}", SLEEP_TIME, (waitTime - SLEEP_TIME));
                Thread.sleep(SLEEP_TIME);
                waitTime = waitTime - SLEEP_TIME;
                tagNames = client.getTagNames(clusterName);
            } else {
                _log.info("Found {} tag on the Array.", volumesTagName);
                break;
            }

        }
        if (!tagNames.contains(volumesTagName)) {
            _log.info("Sending create volume tag request {}", volumesTagName);
            client.createTag(volumesTagName, null, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), clusterName);
        }

        if (!tagNames.contains(snapshotsTagName)) {
            _log.info("Sending create snapshot tag request {}", snapshotsTagName);
            client.createTag(snapshotsTagName, null, XtremIOConstants.XTREMIO_ENTITY_TYPE.SnapshotSet.name(), clusterName);
        } else {
            _log.info("Found {} tag on the Array.", snapshotsTagName);
        }

        return tagNamesMap;

    }

    /**
     * Checks if there are tags with the given name for consistency group.
     * If not found, create them.
     * 
     * @param client
     * @param rootTagName
     * @param clusterName
     * @return string
     * @throws Exception
     */
    public static String createTagsForConsistencyGroup(XtremIOClient client, String rootTagName, String clusterName)
            throws Exception {
        List<String> tagNames = client.getTagNames(clusterName);
        _log.info("Tag Names found on Array : {}", Joiner.on("; ").join(tagNames));
        String cgTagName = XtremIOConstants.V2_CONSISTENCY_GROUP_ROOT_FOLDER.concat(rootTagName);

        long waitTime = 30000; // 30 sec
        int count = 0;
        while (waitTime > 0) {
            count++;
            _log.debug("Retrying {} time to find the cg tag", count);
            if (!tagNames.contains(cgTagName)) {
                _log.debug("sleeping time {} remaining time: {}", SLEEP_TIME, (waitTime - SLEEP_TIME));
                Thread.sleep(SLEEP_TIME);
                waitTime = waitTime - SLEEP_TIME;
                tagNames = client.getTagNames(clusterName);
            } else {
                _log.info("Found cg tag: {} on the Array.", cgTagName);
                break;
            }

        }
        if (!tagNames.contains(cgTagName)) {
            _log.info("Sending create cg tag request {}", cgTagName);
            client.createTag(cgTagName, null, XtremIOConstants.XTREMIO_ENTITY_TYPE.ConsistencyGroup.name(), clusterName);
        } else {
            _log.info("Found {} cg tag on the Array.", cgTagName);
        }

        return cgTagName;

    }

    /**
     * Check the number of volumes under the tag/volume folder.
     * If zero, delete the tag/folder
     * 
     * @param client
     * @param xioClusterName
     * @param volumeFolderName
     * @param storageSystem
     * @throws Exception
     */
    public static void cleanupVolumeFoldersIfNeeded(XtremIOClient client, String xioClusterName, String volumeFolderName,
            StorageSystem storageSystem) throws Exception {
        try {
            boolean isVersion2 = client.isVersion2();
            // Find the # volumes in folder, if the Volume folder is empty,
            // then delete the folder too
            XtremIOTag tag = client.getTagDetails(volumeFolderName, XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
            String numOfVols = isVersion2 ? tag.getNumberOfDirectObjs() : tag.getNumberOfVolumes();
            int numberOfVolumes = Integer.parseInt(numOfVols);
            if (numberOfVolumes == 0) {
                if (isVersion2) {
                    client.deleteTag(volumeFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
                } else {
                    String volumesFolderName = volumeFolderName.concat(XtremIOConstants.VOLUMES_SUBFOLDER);
                    String snapshotsFolderName = volumeFolderName.concat(XtremIOConstants.SNAPSHOTS_SUBFOLDER);
                    _log.info("Deleting Volumes Folder ...");
                    client.deleteTag(volumesFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
                    _log.info("Deleting Snapshots Folder ...");
                    client.deleteTag(snapshotsFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
                    _log.info("Deleting Root Folder ...");
                    client.deleteTag(volumeFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
                }
            }
        } catch (Exception e) {
            _log.warn("Deleting root folder {} failed", volumeFolderName, e);
        }
    }

    /**
     * Get the XtremIO client for making requests to the system based
     * on the passed profile.
     * 
     * @param accessProfile A reference to the access profile.
     * @param xtremioRestClientFactory xtremioclientFactory.
     * 
     * @return A reference to the xtremio client.
     */
    public static XtremIOClient getXtremIOClient(StorageSystem system, XtremIOClientFactory xtremioRestClientFactory) {
        xtremioRestClientFactory.setModel(system.getFirmwareVersion());
        XtremIOClient client = (XtremIOClient) xtremioRestClientFactory
                .getRESTClient(
                        URI.create(XtremIOConstants.getXIOBaseURI(system.getSmisProviderIP(),
                                system.getSmisPortNumber())), system.getSmisUserName(), system.getSmisPassword(), true);
        return client;
    }

    /**
     * Refresh the XIO Providers & its client connections.
     * 
     * @param xioProviderList the XIO provider list
     * @param dbClient the db client
     * @return the list of active providers
     */
    public static List<URI> refreshXtremeIOConnections(final List<StorageProvider> xioProviderList,
            DbClient dbClient, XtremIOClientFactory xtremioRestClientFactory) {
        List<URI> activeProviders = new ArrayList<URI>();
        for (StorageProvider provider : xioProviderList) {
            boolean isConnectionLive = false;
            // We can't determine the client version now, let first scan determine the version.
            if (null == provider.getVersionString()) {
                continue;
            }
            try {
                xtremioRestClientFactory.setModel(provider.getVersionString());
                XtremIOClient clientFromCache = (XtremIOClient) xtremioRestClientFactory.getRESTClient(
                        URI.create(XtremIOConstants.getXIOBaseURI(provider.getIPAddress(),
                                provider.getPortNumber())), provider.getUserName(), provider.getPassword(), true);
                if (null != clientFromCache && null != clientFromCache.getXtremIOXMSVersion()) {
                    isConnectionLive = true;
                } else {
                    _log.debug("Connection from cache is not valid trying with new credentials {}", provider.getProviderID());
                    // remove the existing client connection
                    xtremioRestClientFactory.removeRESTClient(
                            URI.create(XtremIOConstants.getXIOBaseURI(provider.getIPAddress(),
                                    provider.getPortNumber())), provider.getUserName(), provider.getPassword());
                    // Initialize with the new provider credentials.
                    XtremIOClient newXIOClient = (XtremIOClient) xtremioRestClientFactory.getRESTClient(
                            URI.create(XtremIOConstants.getXIOBaseURI(provider.getIPAddress(),
                                    provider.getPortNumber())), provider.getUserName(), provider.getPassword(), true);
                    if (null != newXIOClient.getXtremIOXMSVersion()) {
                        isConnectionLive = true;
                    }
                }
                // Now update provider status based on connection live check.
                if (isConnectionLive) {
                    provider.setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED
                            .toString());
                    activeProviders.add(provider.getId());
                } else {
                    _log.info("XIO Connection is not active {}", provider.getProviderID());
                    provider.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED
                            .toString());
                }
            } catch (Exception ex) {
                _log.error("Exception occurred while validating xio client for {}", provider.getProviderID(), ex);
                provider.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED
                        .toString());
            } finally {
                dbClient.updateObject(provider);
            }
        }
        return activeProviders;
    }

    public static boolean is4xXtremIOModel(String model) {
        return (null != model && Integer.valueOf(model.split(DOT_OPERATOR)[0]) >= XIO_MIN_4X_VERSION);
    }
}
