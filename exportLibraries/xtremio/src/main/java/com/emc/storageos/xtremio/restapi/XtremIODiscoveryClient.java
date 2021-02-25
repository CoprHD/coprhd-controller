/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.util.List;
import java.util.Set;

import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroupVolInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOLunMap;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOLunMapFull;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPerformanceResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPort;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTag;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumesFull;

public interface XtremIODiscoveryClient {

    /**
     * Get information on all the clusters configured.
     *
     * @return
     * @throws Exception
     */
    public List<XtremIOSystem> getXtremIOSystemInfo() throws Exception;

    /**
     * Get the XMS version
     *
     * @return XMS version
     * @throws Exception
     */

    public String getXtremIOXMSVersion() throws Exception;

    /**
     * Get the targets associated with the cluster
     *
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<XtremIOPort> getXtremIOPortInfo(String clusterName) throws Exception;

    /**
     * Get the initiators of the cluster
     *
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<XtremIOInitiator> getXtremIOInitiatorsInfo(String clusterName) throws Exception;

    /**
     * Get all the volume and their details for the given cluster
     *
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<XtremIOVolume> getXtremIOVolumes(String clusterName) throws Exception;

    /**
     * Get the volume details for the passed volume links
     *
     * @param volumeLinks
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<XtremIOVolume> getXtremIOVolumesForLinks(List<XtremIOObjectInfo> volumeLinks, String clusterName)
            throws Exception;

    /**
     * Get all the volume links for the given cluster
     *
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<XtremIOObjectInfo> getXtremIOVolumeLinks(String clusterName) throws Exception;

    /**
     * Get all the lun map and their details for the given cluster.
     *
     * @param clusterName
     * @return lun maps
     * @throws Exception
     */
    public List<XtremIOLunMap> getXtremIOLunMaps(String clusterName) throws Exception;

    /**
     * Get all the lun map links for the given cluster
     *
     * @param clusterName
     * @return lun map links
     * @throws Exception
     */
    public List<XtremIOObjectInfo> getXtremIOLunMapLinks(String clusterName) throws Exception;

    /**
     * Get the lun map details for the passed lun map links
     *
     * @param clusterName
     * @return lun maps
     * @throws Exception
     */
    public List<XtremIOLunMap> getXtremIOLunMapsForLinks(List<XtremIOObjectInfo> lunMapLinks, String clusterName) throws Exception;
    
    /**
     * Get all the Consistency groups for a given cluster
     *
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<XtremIOObjectInfo> getXtremIOConsistencyGroups(String clusterName) throws Exception;

    /**
     * Get all the Consistency groups for a given cluster
     *
     * @param cgVolume
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOConsistencyGroupVolInfo getXtremIOConsistencyGroupInfo(XtremIOObjectInfo cgVolume, String clusterName) throws Exception;

    /**
     * Get all the volume folder names of the given cluster. This is relevant only for version 1 REST API
     *
     * @return
     * @throws Exception
     */
    public List<String> getVolumeFolderNames() throws Exception;

    /**
     * Get the tag names created in the given cluster. This is relevant only for version 2 REST API.
     *
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<String> getTagNames(String clusterName) throws Exception;

    /**
     * Get the initiator details for the given initiator name and cluster
     *
     * @param initiatorName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOInitiator getInitiator(String initiatorName, String clusterName) throws Exception;

    /**
     * Get the initiator group details given the initiator group name and cluster
     *
     * @param initiatorGroupName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOInitiatorGroup getInitiatorGroup(String initiatorGroupName, String clusterName) throws Exception;

    /**
     * Get the volume details for the given name and cluster
     *
     * @param volumeName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOVolume getVolumeDetails(String volumeName, String clusterName) throws Exception;

    /**
     * Get the snapshot details for the given name and cluster
     *
     * @param snapName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOVolume getSnapShotDetails(String snapName, String clusterName) throws Exception;

    /**
     * Get the consistency group details for the given name and cluster
     *
     * @param cgName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOConsistencyGroup getConsistencyGroupDetails(String cgName, String clusterName) throws Exception;

    /**
     * Get the cluster details for the given serial number
     *
     * @param clusterSerialNumber
     * @return
     * @throws Exception
     */
    public XtremIOSystem getClusterDetails(String clusterSerialNumber) throws Exception;

    /**
     * Get the tag details for the given name and cluster
     *
     * @param tagName
     * @param tagEntityType
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOTag getTagDetails(String tagName, String tagEntityType, String clusterName) throws Exception;

    /**
     * Get the object performance metrics for the given cluster and entity.
     * Additional parameters are provided in the order
     *  param-3 name, param-3 value, param-4 name, param-4 value and so on.
     *
     * @param clusterName the cluster name
     * @param entityName the entity name
     * @param parameters the parameters
     * @return the xtremio object performance
     * @throws Exception the exception
     */
    public XtremIOPerformanceResponse getXtremIOObjectPerformance(String clusterName,
            String entityName, String... parameters) throws Exception;

    /**
     * Get the snapshot set details for the given name and cluster
     *
     * @param snapshotSetName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOConsistencyGroup getSnapshotSetDetails(String snapshotSetName, String clusterName) throws Exception;

    /**
     *
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<XtremIOObjectInfo> getLunMaps(String clusterName) throws Exception;

    /**
     *
     * @param igName
     * @param clusterName
     * @return
     * @throws Exception
     */
    public List<XtremIOObjectInfo> getLunMapsForInitiatorGroup(String igName, String clusterName) throws Exception;

    public List<XtremIOLunMapFull> getLunMapsForAllInitiatorGroups(Set<String> igNameSet, String clusterName) throws Exception;

    public XtremIOVolumesFull getVolumesForAllInitiatorGroups(String clusterName, StringBuilder volumeURL) throws Exception;
    /**
     *
     * @param index
     * @param clusterName
     * @return
     * @throws Exception
     */
    public XtremIOVolume getVolumeByIndex(String index, String clusterName) throws Exception;

}
