/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to update the SRDF Target volumes with the source RDFGroup URI instead of target RDFGroup
 * for all ingested volumes in 2.3 version.
 * 
 * This handles both 4.x & 8.x versions.
 * 
 */
public class SRDFTargetVolumeRDFGroupMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(SRDFTargetVolumeRDFGroupMigration.class);

    public static final String SMIS_DOT_REGEX = "\\.";
    public static final String SMIS_PLUS_REGEX = "\\+";
    public static final String PLUS = "+";

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Updating SRDF Target volume rdfGroup information.");
        DbClient dbClient = this.getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, true);
        Map<URI, RemoteDirectorGroup> rdfGroupCache = new HashMap<URI, RemoteDirectorGroup>();
        Map<URI, StorageSystem> systemCache = new HashMap<URI, StorageSystem>();
        List<Volume> volumesToUpdate = new ArrayList<Volume>();
        Iterator<Volume> volumes =
                dbClient.queryIterativeObjects(Volume.class, volumeURIs);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            try {
                if (null != volume.getSrdfParent() && !NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent())) {
                    if (null != volume.getSrdfGroup() && !NullColumnValueGetter.isNullURI(volume.getSrdfGroup())) {
                        log.info("Determining SRDF Target volume {} to update rdf group", volume.getLabel());
                        RemoteDirectorGroup volumeSrdfGroup = fetchRDFGroupFromCache(rdfGroupCache, volume.getSrdfGroup());
                        StorageSystem system = fetchSystemFromCache(systemCache, volume.getStorageController());
                        // Found a target volume with the target SRDFGroup uri
                        if (URIUtil.identical(volumeSrdfGroup.getSourceStorageSystemUri(), volume.getStorageController())) {
                            // Set the source SRDF Group URI
                            RemoteDirectorGroup sourceRDFGroup = getAssociatedTargetRemoteDirectorGroup(system.getUsingSmis80(),
                                    volumeSrdfGroup.getNativeGuid());
                            if (null == sourceRDFGroup) {
                                log.info("Source RDFGroup not found in DB. Hence skipping.");
                                continue;
                            }
                            volume.setSrdfGroup(sourceRDFGroup.getId());
                            volumesToUpdate.add(volume);
                            if (volumesToUpdate.size() > 100) {
                                this.dbClient.updateObject(volumesToUpdate);
                                log.info("Updated {} SRDF Target volumes in db", volumesToUpdate.size());
                                volumesToUpdate.clear();
                            }
                        } else {
                            log.info("No need to update the rdfgroup for volume {} as it has the right source RDFGroup {}",
                                    volume.getLabel(),
                                    volume.getSrdfGroup());
                        }

                    }

                }
            } catch (Exception ex) {
                log.error("Exception occurred while updating the SRDFGroup for the target volume {}. proceeding next..", volume.getLabel());
            }
        }
        // Update the remaining volumes
        if (volumesToUpdate.size() > 0) {
            this.dbClient.updateObject(volumesToUpdate);
            log.info("Updated {} SRDF Target volumes in db", volumesToUpdate.size());
        }
    }

    /**
     * Return the storageSystem if it is found in cache otherwise query from db.
     * 
     * @param systemCache
     * @param storageController
     * @return
     */
    private StorageSystem fetchSystemFromCache(Map<URI, StorageSystem> systemCache, URI storageController) {
        if (systemCache.containsKey(storageController)) {
            return systemCache.get(storageController);
        }
        StorageSystem system = this.getDbClient().queryObject(StorageSystem.class, storageController);
        if (null != system && !system.getInactive()) {
            systemCache.put(storageController, system);
        }
        return system;
    }

    /**
     * Return the RemoteDirectorGroup from cache otherwise query from db.
     * 
     * @param rdfGroupCache
     * @param srdfGroupURI
     * @return
     */
    private RemoteDirectorGroup fetchRDFGroupFromCache(Map<URI, RemoteDirectorGroup> rdfGroupCache, URI srdfGroupURI) {
        if (rdfGroupCache.containsKey(srdfGroupURI)) {
            return rdfGroupCache.get(srdfGroupURI);
        }
        RemoteDirectorGroup rdfGroup = this.getDbClient().queryObject(RemoteDirectorGroup.class, srdfGroupURI);
        if (null != rdfGroup && !rdfGroup.getInactive()) {
            rdfGroupCache.put(srdfGroupURI, rdfGroup);
        }
        return rdfGroup;
    }

    /**
     * Gets the associated target remote director group
     * by forming target RDF group's NativeGuid from source group NativeGuid
     */
    private RemoteDirectorGroup getAssociatedTargetRemoteDirectorGroup(boolean is80Provider, String raGroupId) {
        // interchange source and target ids & group ids
        // 8.0.x NativeGuid format in DB
        // SYMMETRIX+000195700985+REMOTEGROUP+000195700985+60+000195700999+60
        // SYMMETRIX+000195700999+REMOTEGROUP+000195700999+60+000195700985+60

        // 4.6.x NativeGuid format in DB
        // SYMMETRIX+000195701573+REMOTEGROUP+000195701505+60+000195701573+60
        // SYMMETRIX+000195701505+REMOTEGROUP+000195701505+60+000195701573+60

        String targetRaGroupNativeGuid = null;
        StringBuilder strBuilder = new StringBuilder();
        String[] nativeGuidArray = raGroupId.split(SMIS_PLUS_REGEX);
        String sourceArray = nativeGuidArray[1];
        if (is80Provider) {
            String targetArray = nativeGuidArray[5];
            strBuilder.append(nativeGuidArray[0]).append(PLUS)
                    .append(targetArray).append(PLUS)
                    .append(nativeGuidArray[2]).append(PLUS)
                    .append(targetArray).append(PLUS)
                    .append(nativeGuidArray[6]).append(PLUS)
                    .append(sourceArray).append(PLUS)
                    .append(nativeGuidArray[4]);
        } else {
            String targetArray = null;
            if (nativeGuidArray[3].contains(sourceArray)) {
                targetArray = nativeGuidArray[5];
            } else {
                targetArray = nativeGuidArray[3];
            }
            strBuilder.append(nativeGuidArray[0]).append(PLUS)
                    .append(targetArray).append(PLUS)
                    .append(nativeGuidArray[2]).append(PLUS)
                    .append(nativeGuidArray[3]).append(PLUS)
                    .append(nativeGuidArray[6]).append(PLUS)
                    .append(nativeGuidArray[5]).append(PLUS)
                    .append(nativeGuidArray[4]);
        }
        targetRaGroupNativeGuid = strBuilder.toString();
        log.debug("Target RA Group Id : {}", targetRaGroupNativeGuid);
        RemoteDirectorGroup remoteGroup = getRAGroupFromDB(targetRaGroupNativeGuid);
        if (null == remoteGroup) {
            log.warn("Target RA Group {} not found", targetRaGroupNativeGuid);
            return null;
        }
        return remoteGroup;
    }

    /**
     * Return the RemoteDirectorGroup based on the nativeGuid.
     * 
     * @param raGroupNativeGuid
     * @return
     */
    private RemoteDirectorGroup getRAGroupFromDB(String raGroupNativeGuid) {
        URIQueryResultList raGroupUris = new URIQueryResultList();
        this.getDbClient().queryByConstraint(AlternateIdConstraint.Factory.getRAGroupByNativeGuidConstraint(raGroupNativeGuid),
                raGroupUris);
        for (URI raGroupURI : raGroupUris) {
            RemoteDirectorGroup raGroup = dbClient.queryObject(RemoteDirectorGroup.class, raGroupURI);
            if (null != raGroup && !raGroup.getInactive()) {
                return raGroup;
            }
        }
        return null;
    }

}
