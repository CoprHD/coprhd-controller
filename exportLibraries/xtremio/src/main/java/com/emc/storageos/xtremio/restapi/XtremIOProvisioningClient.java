/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import com.emc.storageos.xtremio.restapi.model.response.XtremIOResponse;

public interface XtremIOProvisioningClient {

    /**
     * Create a tag.
     * 
     * @param tagName
     * @param parentTag
     * @param entityType
     * @param clusterName
     * @throws Exception
     */
    public void createTag(String tagName, String parentTag, String entityType, String clusterName) throws Exception;

    /**
     * Delete a tag
     * 
     * @param tagName
     * @param tagEntityType
     * @param clusterName
     * @throws Exception
     */
    public void deleteTag(String tagName, String tagEntityType, String clusterName) throws Exception;

    /**
     * Tag an object with the given tag name
     * 
     * @param tagName
     * @param entityType
     * @param entityDetail
     * @param clusterName
     * @throws Exception
     */
    public void tagObject(String tagName, String entityType, String entityDetail, String clusterName) throws Exception;

    /**
     * Creates a volume
     * 
     * @param volumeName
     * @param size
     * @param parentFolderName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOResponse createVolume(String volumeName, String size, String parentFolderName, String clusterName)
            throws Exception;

    /**
     * Deletes a volume
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void deleteVolume(String volumeName, String clusterName) throws Exception;

    /**
     * Expands a volume
     * 
     * @param volumeName
     * @param size
     * @param clusterName
     * @throws Exception
     */
    public void expandVolume(String volumeName, String size, String clusterName) throws Exception;

    /**
     * Creates a snapshot
     * 
     * @param parentVolumeName
     * @param snapName
     * @param folderName
     * @param snapType
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOResponse createVolumeSnapshot(String parentVolumeName, String snapName, String folderName, String snapType,
            String clusterName) throws Exception;

    /**
     * Creates a snapshot for the consistency group
     * 
     * @param consistencyGroupName
     * @param snapshotSetName
     * @param folderName
     * @param snapType
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOResponse createConsistencyGroupSnapshot(String consistencyGroupName, String snapshotSetName, String folderName,
            String snapType, String clusterName) throws Exception;

    /**
     * Deletes a snapshot
     * 
     * @param snapName
     * @param clusterName
     * @throws Exception
     */
    public void deleteSnapshot(String snapName, String clusterName) throws Exception;

    /**
     * Deletes a snapshot set
     * 
     * @param snapshotSetName
     * @param clusterName
     * @throws Exception
     */
    public void deleteSnapshotSet(String snapshotSetName, String clusterName) throws Exception;

    /**
     * Creates an initiator & add it to InitiatorGroup.
     * 
     * @param initiatorName - InitiatorName to register.
     * @param igId - InitiatorGroup to add initiator.
     * @param portAddress - Initiator wwn.
     * @param os - Operation-System of Host to register.
     * @param clusterName - XIO clusterName.
     * @return
     * @throws Exception
     */
    public XtremIOResponse createInitiator(String initiatorName, String igId, String portAddress, String os, String clusterName)
            throws Exception;

    /**
     * Delete initiator
     * 
     * @param initiatorName
     * @throws Exception
     */
    public void deleteInitiator(String initiatorName, String clusterName) throws Exception;

    /**
     * Creates a new XtremIO IG
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void createInitiatorGroup(String igName, String parentFolderId, String clusterName) throws Exception;

    /**
     * Deletes the initiator group
     * 
     * @param igName
     * @param clusterName
     * @throws Exception
     */
    public void deleteInitiatorGroup(String igName, String clusterName) throws Exception;

    /**
     * Creates a lun map
     * 
     * @param volName
     * @param igName
     * @param hlu
     * @param clusterName
     * @throws Exception
     */
    public void createLunMap(String volName, String igName, String hlu, String clusterName) throws Exception;

    /**
     * Deletes a lunMap
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void deleteLunMap(String lunMap, String clusterName) throws Exception;

    /**
     * Creates a consistency group
     * 
     * @param cgName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOResponse createConsistencyGroup(String cgName, String clusterName) throws Exception;

    /**
     * Deletes the consistency group
     * 
     * @param cgName
     * @param clusterName
     * @throws Exception
     */
    public void removeConsistencyGroup(String cgName, String clusterName) throws Exception;

    /**
     * Add volume to the consistency group
     * 
     * @param volName
     * @param cgName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOResponse addVolumeToConsistencyGroup(String volName, String cgName, String clusterName)
            throws Exception;

    /**
     * Remove volume from the consistency group
     * 
     * @param volName
     * @param cgName
     * @param clusterName
     * @throws Exception
     */
    public void removeVolumeFromConsistencyGroup(String volName, String cgName, String clusterName)
            throws Exception;

    /**
     * Restores the volume from the snapshot
     * 
     * @param clusterName
     * @param volName
     * @param snapshotName
     * @return
     * @throws Exception
     */
    public XtremIOResponse restoreVolumeFromSnapshot(String clusterName, String volName, String snapshotName) throws Exception;

    /**
     * Restores the CG from the snapshot set
     * 
     * @param clusterName
     * @param cgName
     * @param snapshotName
     * @return
     * @throws Exception
     */
    public XtremIOResponse restoreCGFromSnapshot(String clusterName, String cgName, String snapshotName) throws Exception;

    /**
     * refresh the snapshot from the parent volume
     * 
     * @param clusterName
     * @param volName
     * @param snapshotName
     * @return
     * @throws Exception
     */
    public XtremIOResponse refreshSnapshotFromVolume(String clusterName, String volName, String snapshotName) throws Exception;

    /**
     * Refresh the snapshot set from the CG
     * 
     * @param clusterName
     * @param cgName
     * @param snapshotName
     * @return
     * @throws Exception
     */
    public XtremIOResponse refreshSnapshotFromCG(String clusterName, String cgName, String snapshotName) throws Exception;

}
