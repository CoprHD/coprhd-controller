/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.xtremio.prov.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;

public class XtremIOProvUtils {

    private static final Logger _log = LoggerFactory.getLogger(XtremIOProvUtils.class);

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

    public static XtremIOVolume isVolumeAvailableInArray(XtremIOClient client, String label, String clusterName) {
        XtremIOVolume volume = null;
        try {
            volume = client.getVolumeDetails(label, clusterName);
        } catch (Exception e) {
            _log.info("Volume {} already deleted.", label);
        }
        return volume;
    }

    public static XtremIOVolume isSnapAvailableInArray(XtremIOClient client, String label, String clusterName) {
        XtremIOVolume volume = null;
        try {
            volume = client.getSnapShotDetails(label, clusterName);
        } catch (Exception e) {
            _log.info("Snapshot {} not available in Array.", label);
        }
        return volume;
    }
    
    public static XtremIOConsistencyGroup isCGAvailableInArray(XtremIOClient client, String label, String clusterName) {
    	XtremIOConsistencyGroup cg = null;
    	try {
    		cg = client.getConsistencyGroupDetails(label, clusterName);
    	} catch (Exception e) {
            _log.info("Consistency group {} not available in Array.", label);
        }
    	
    	return cg;
    }
    
    public static Map<String, String> createFoldersForVolumeAndSnaps(XtremIOClient client, String rootVolumeFolderName)
            throws Exception {
        
        List<String> folderNames = client.getVolumeFolderNames();
        _log.info("Volume folder Names found on Array : {}", Joiner.on("; ").join(folderNames));
        Map<String, String> folderNamesMap = new HashMap<String, String>();
        String rootFolderName = XtremIOConstants.ROOT_FOLDER.concat(rootVolumeFolderName);
        _log.info("rootVolumeFolderName: {}", rootFolderName);
        String volumesFolderName = rootFolderName.concat(XtremIOConstants.VOLUMES_SUBFOLDER);
        String snapshotsFolderName = rootFolderName.concat(XtremIOConstants.SNAPSHOTS_SUBFOLDER);
        folderNamesMap.put(XtremIOConstants.VOLUME_KEY, volumesFolderName);
        folderNamesMap.put(XtremIOConstants.SNAPSHOT_KEY, snapshotsFolderName);
        if (!folderNames.contains(rootFolderName)) {
            _log.info("Sending create root folder request {}", rootFolderName);
            client.createVolumeFolder(rootVolumeFolderName, "/");
        } else {
            _log.info("Found {} folder on the Array.", rootFolderName);
        }

        if (!folderNames.contains(volumesFolderName)) {
            _log.info("Sending create volume folder request {}", volumesFolderName);
            client.createVolumeFolder("volumes", rootFolderName);
        } else {
            _log.info("Found {} folder on the Array.", volumesFolderName);
        }

        if (!folderNames.contains(snapshotsFolderName)) {
            _log.info("Sending create snapshot folder request {}", snapshotsFolderName);
            client.createVolumeFolder("snapshots", rootFolderName);
        } else {
            _log.info("Found {} folder on the Array.", snapshotsFolderName);
        }

        return folderNamesMap;
    }
    
    public static Map<String, String> createTagsForVolumeAndSnaps(XtremIOClient client, String rootTagName, String clusterName)
            throws Exception {
        List<String> tagNames = client.getTagNames(clusterName);
        _log.info("Tag Names found on Array : {}", Joiner.on("; ").join(tagNames));
        Map<String, String> tagNamesMap = new HashMap<String, String>();
        
        String rootVolumeTagName = XtremIOConstants.V2_VOLUME_ROOT_FOLDER.concat(rootTagName);
        _log.info("rootTagFolderName: {}", rootTagName);
        String volumesTagName = rootVolumeTagName.concat(XtremIOConstants.VOLUMES_SUBFOLDER);
        String snapshotsTagName = rootVolumeTagName.concat(XtremIOConstants.SNAPSHOTS_SUBFOLDER);
        tagNamesMap.put(XtremIOConstants.VOLUME_KEY, volumesTagName);
        tagNamesMap.put(XtremIOConstants.SNAPSHOT_KEY, snapshotsTagName);
        
        if (!tagNames.contains(volumesTagName)) {
            _log.info("Sending create volume tag request {}", volumesTagName);
            client.createTag(volumesTagName, XtremIOConstants.XTREMIO_TAG_ENTITY.Volume, clusterName);
        } else {
            _log.info("Found {} tag on the Array.", volumesTagName);
        }
        
        if (!tagNames.contains(snapshotsTagName)) {
            _log.info("Sending create snapshot tag request {}", snapshotsTagName);
            client.createTag(snapshotsTagName, XtremIOConstants.XTREMIO_TAG_ENTITY.Snapshot, clusterName);
        } else {
            _log.info("Found {} tag on the Array.", snapshotsTagName);
        }
        
        return tagNamesMap;
        
    }
}
