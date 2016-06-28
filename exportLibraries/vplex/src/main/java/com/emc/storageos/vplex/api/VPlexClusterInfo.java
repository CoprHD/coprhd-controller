/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.vplex.api.clientdata.VolumeInfo;

/**
 * Info for a VPlex cluster
 */
public class VPlexClusterInfo extends VPlexResourceInfo {

    // The top level assembly identifier.
    private String topLevelAssembly;

    // The cluster id (1 or 2)
    private String clusterId;

    // Information about the storage systems accessible to the cluster.
    private List<VPlexStorageSystemInfo> storageSystemInfoList = new ArrayList<VPlexStorageSystemInfo>();

    // Information about the storage volumes accessible to the cluster.
    private List<VPlexStorageVolumeInfo> storageVolumeInfoList = new ArrayList<VPlexStorageVolumeInfo>();

    // Information about the system volumes accessible to the cluster.
    private List<VPlexSystemVolumeInfo> systemVolumeInfoList = new ArrayList<VPlexSystemVolumeInfo>();
    
	/**
     * Getter for the assembly id.
     * 
     * @return The cluster assembly id.
     */
    public String getTopLevelAssembly() {
        return topLevelAssembly;
    }

    /**
     * Setter for the assembly id.
     * 
     * @param id The cluster assembly id.
     */
    public void setTopLevelAssembly(String id) {
        topLevelAssembly = id;
    }

    /**
     * Getter for the cluster id (1 or 2).
     * 
     * @return The cluster id.
     */
    public String getClusterId() {
        return clusterId;
    }

    /**
     * Setter for the cluster id.
     * 
     * @param id The cluster id.
     */
    public void setClusterId(String id) {
        clusterId = id;
    }

    /**
     * Getter for the storage system info for the cluster.
     * 
     * @return The storage system info for the cluster.
     */
    public List<VPlexStorageSystemInfo> getStorageSystemInfo() {
        return storageSystemInfoList;
    }

    /**
     * Setter for the storage system info for the cluster.
     * 
     * @param systemInfoList The storage system info for the cluster.
     */
    public void setStorageSystemInfo(List<VPlexStorageSystemInfo> systemInfoList) {
        storageSystemInfoList = systemInfoList;
    }

    /**
     * Determines if the cluster is managing a storage system with the passed
     * name.
     * 
     * @param storageSystemGuid The storage system guid to check.
     * 
     * @return true if the cluster is managing a storage system with the passed
     *         guid, else false.
     */
    public boolean containsStorageSystem(String storageSystemGuid) {
        boolean contains = false;
        for (VPlexStorageSystemInfo storageSystemInfo : storageSystemInfoList) {
            if (storageSystemInfo.matches(storageSystemGuid)) {
                contains = true;
                break;
            }
        }
        return contains;
    }

    /**
     * Getter for the storage volume info for the cluster.
     * 
     * @return The storage volume info for the cluster.
     */
    public List<VPlexStorageVolumeInfo> getStorageVolumeInfo() {
        return storageVolumeInfoList;
    }

    /**
     * Setter for the storage volume info for the cluster.
     * 
     * @param volumeInfoList The storage volume info for the cluster.
     */
    public void setStorageVolumeInfo(List<VPlexStorageVolumeInfo> volumeInfoList) {
        storageVolumeInfoList = volumeInfoList;
    }

    /**
     * Gets the storage volume with the passed name.
     * 
     * @param storageVolumeName The storage volume name.
     * 
     * @return A reference to the VPlexStorageVolumeInfo for the requested volume or null if not found.
     */
    public VPlexStorageVolumeInfo getStorageVolume(VolumeInfo volumeInfo) {
        String storageSystemNativeGuid = volumeInfo.getStorageSystemNativeGuid();
        String volumeWWN = volumeInfo.getVolumeWWN();
        List<String> volumeItlsList = volumeInfo.getITLs();
        s_logger.info(String.format("Getting storage volume with native info [%s : %s : %s]", storageSystemNativeGuid, volumeWWN, volumeItlsList));
        for (VPlexStorageVolumeInfo clusterStorageVolumeInfo : storageVolumeInfoList) {
            String clusterVolumeWWN = clusterStorageVolumeInfo.getWwn();
            List<String> clusterVolumeItls = clusterStorageVolumeInfo.getItls();
            s_logger.info(String.format("Cluster storage volume info [%s : %s : %s]", clusterStorageVolumeInfo.getName(), clusterVolumeWWN, clusterVolumeItls));
            if ((null != volumeItlsList) && (!volumeItlsList.isEmpty())) { 
                if ((null != clusterVolumeItls) && (!clusterVolumeItls.isEmpty())) {
                    for (String itlPair : volumeItlsList) {
                        // If any one of the pair matches that is the volume.
                        if (clusterVolumeItls.contains(itlPair.trim().toLowerCase())) {
                            return clusterStorageVolumeInfo;
                        }
                    }
                }
            } else if (storageSystemNativeGuid.contains(VPlexApiConstants.HDS_SYSTEM)) {
                if (clusterVolumeWWN.endsWith(volumeWWN.toLowerCase())) {
                    return clusterStorageVolumeInfo;
                }
            } else if (clusterVolumeWWN.equals(volumeWWN.toLowerCase())) {
                return clusterStorageVolumeInfo;
            }
        }
        
        return null;
    }
    

    /**
     * Getter for the system volume info for the cluster.
     * 
     * @return The system volume info for the cluster.
     */
    public List<VPlexSystemVolumeInfo> getSystemVolumeInfo() {
        return systemVolumeInfoList;
    }

    /**
     * Setter for the system volume info for the cluster.
     * 
     * @param volumeInfoList The system volume info for the cluster.
     */
    public void setSystemVolumeInfo(List<VPlexSystemVolumeInfo> volumeInfoList) {
        systemVolumeInfoList = volumeInfoList;
    }

    /**
     * Determines whether or not the cluster has a system volume that is a
     * logging volume.
     * 
     * @return true if the cluster has a logging volume, false otherwise.
     */
    public boolean hasLoggingVolume() {
        boolean hasLoggingVolume = false;
        for (VPlexSystemVolumeInfo systemVolumeInfo : systemVolumeInfoList) {
            if (VPlexApiConstants.LOGGING_VOLUME_TYPE.equals(systemVolumeInfo.getType())) {
                hasLoggingVolume = true;
                break;
            }
        }

        return hasLoggingVolume;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("ClusterInfo ( ");
        str.append(super.toString());
        str.append(", assemblyId: ").append(topLevelAssembly);
        str.append(", clusterId: ").append(clusterId);
        for (VPlexStorageSystemInfo storageSystemInfo : storageSystemInfoList) {
            str.append(", ");
            str.append(storageSystemInfo.toString());
        }
        for (VPlexStorageVolumeInfo storageVolumeInfo : storageVolumeInfoList) {
            str.append(", ");
            str.append(storageVolumeInfo.toString());
        }
        for (VPlexSystemVolumeInfo systemVolumeInfo : systemVolumeInfoList) {
            str.append(", ");
            str.append(systemVolumeInfo.toString());
        }
        str.append(" )");

        return str.toString();
    }
}
