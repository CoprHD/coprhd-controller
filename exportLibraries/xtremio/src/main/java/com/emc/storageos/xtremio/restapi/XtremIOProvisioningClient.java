package com.emc.storageos.xtremio.restapi;

import com.emc.storageos.xtremio.restapi.model.response.XtremIOResponse;

public interface XtremIOProvisioningClient {
    
    public void createTag(String tagName, String parentTag, String entityType, String clusterName) throws Exception;
    
    public void deleteTag(String tagName, String tagEntityType, String clusterName) throws Exception;

    public void tagObject(String tagName, String entityType, String entityDetail, String clusterName) throws Exception;

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
    
    public void expandVolume(String volumeName, String size, String clusterName) throws Exception;
    
    public XtremIOResponse createVolumeSnapshot(String parentVolumeName, String snapName, String folderName, String snapType, 
            String clusterName) throws Exception;
    
    public XtremIOResponse createConsistencyGroupSnapshot(String consistencyGroupName, String snapshotSetName, String folderName, 
            String snapType, String clusterName) throws Exception;

    public void deleteSnapshot(String snapName, String clusterName) throws Exception;

    public void deleteSnapshotSet(String snapshotSetName, String clusterName) throws Exception;
    
    public XtremIOResponse createInitiator(String initiatorName, String igId, String portAddress, String clusterName)
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

    public void deleteInitiatorGroup(String igName, String clusterName) throws Exception;
    
    public void createLunMap(String volName, String igName, String hlu, String clusterName) throws Exception;

    /**
     * Deletes a lunMap
     * 
     * @param projectName
     * @return
     * @throws Exception
     */
    public void deleteLunMap(String lunMap, String clusterName) throws Exception;
    
    public XtremIOResponse createConsistencyGroup(String cgName, String clusterName) throws Exception;
    
    public void removeConsistencyGroup(String cgName, String clusterName) throws Exception;
    
    public XtremIOResponse addVolumeToConsistencyGroup(String volName, String cgName, String clusterName) 
            throws Exception;
    
    public void removeVolumeFromConsistencyGroup(String volName, String cgName, String clusterName) 
            throws Exception;
    
    public XtremIOResponse restoreVolumeFromSnapshot(String clusterName, String volName, String snapshotName) throws Exception;
    
    public XtremIOResponse restoreCGFromSnapshot(String clusterName, String cgName, String snapshotName) throws Exception;
    
    public XtremIOResponse refreshSnapshotFromVolume(String clusterName, String volName, String snapshotName) throws Exception;
    
    public XtremIOResponse refreshSnapshotFromCG(String clusterName, String cgName, String snapshotName) throws Exception;

}
