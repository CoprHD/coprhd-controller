package com.emc.storageos.xtremio.restapi;

import java.util.List;

import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOPort;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTag;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumeInfo;

public interface XtremIODiscoveryClient {
    
    /**
     * Get information on all the clusters configured. 
     * 
     * @return
     * @throws Exception
     */
    public List<XtremIOSystem> getXtremIOSystemInfo() throws Exception;
    
    public String getXtremIOXMSVersion() throws Exception;

    public List<XtremIOPort> getXtremIOPortInfo(String clusterName) throws Exception;
    
    public List<XtremIOInitiator> getXtremIOInitiatorsInfo(String clusterName) throws Exception;
    
    public List<XtremIOVolume> getXtremIOVolumes(String clusterName) throws Exception;

    public List<XtremIOVolume> getXtremIOVolumesForLinks(List<XtremIOVolumeInfo> volumeLinks, String clusterName) 
            throws Exception;
    
    public List<XtremIOVolumeInfo> getXtremIOVolumeLinks(String clusterName) throws Exception;

    public List<String> getVolumeFolderNames() throws Exception;
    
    public List<String> getTagNames(String clusterName) throws Exception;

    public XtremIOInitiator getInitiator(String initiatorName, String clusterName) throws Exception;

    public XtremIOInitiatorGroup getInitiatorGroup(String initiatorGroupName, String clusterName) throws Exception;

    public XtremIOVolume getVolumeDetails(String volumeName, String clusterName) throws Exception;

    public XtremIOVolume getSnapShotDetails(String snapName, String clusterName) throws Exception;
    
    public XtremIOConsistencyGroup getConsistencyGroupDetails(String cgName, String clusterName) throws Exception;
    
    public XtremIOSystem getClusterDetails(String clusterSerialNumber) throws Exception;
    
    public XtremIOTag getTagDetails(String tagName, String tagEntityType, String clusterName) throws Exception;
    
    public XtremIOConsistencyGroup getSnapshotSetDetails(String snapshotSetName, String clusterName) throws Exception;

}
