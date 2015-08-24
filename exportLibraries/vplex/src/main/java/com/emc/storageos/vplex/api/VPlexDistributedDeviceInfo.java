/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Info for a VPlex distributed device.
 */
public class VPlexDistributedDeviceInfo extends VPlexResourceInfo {

    // The device geometry (RAID level).
    private String geometry = null;

    // The local devices which comprise the distributed device.
    List<VPlexDeviceInfo> localDeviceInfoList = new ArrayList<VPlexDeviceInfo>();

    /**
     * Getter for the device geometry (RAID level).
     * 
     * @return The device geometry.
     */
    public String getGeometry() {
        return geometry;
    }

    /**
     * Setter for the device geometry (RAID level).
     * 
     * @param id The device geometry.
     */
    public void setGeometry(String geometry) {
        this.geometry = geometry;
    }

    /**
     * Getter for the local device info for the device.
     * 
     * @return The local device info for the device.
     */
    public List<VPlexDeviceInfo> getLocalDeviceInfo() {
        return localDeviceInfoList;
    }

    /**
     * Setter for the local device info for the device.
     * 
     * @param infoList The local device info for the device.
     */
    public void setLocalDeviceInfo(List<VPlexDeviceInfo> infoList) {
        localDeviceInfoList = infoList;
    }

    /**
     * Returns the cluster id for the distributed device.
     * The cluster for either local device can be returned.
     * 
     * @return The id of the cluster for the distributed device.
     */
    public String getClusterId() throws VPlexApiException {
        if (!localDeviceInfoList.isEmpty()) {
            return localDeviceInfoList.get(0).getCluster();
        } else {
            throw new VPlexApiException(String.format(
                    "Can't find cluster id for distributed device %s", getName()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("DistributedDeviceInfo ( ");
        str.append(super.toString());
        str.append(", geometry: " + geometry);
        for (VPlexDeviceInfo localDeviceInfo : localDeviceInfoList) {
            str.append(", ");
            str.append(localDeviceInfo.toString());
        }
        str.append(" )");

        return str.toString();
    }
}
