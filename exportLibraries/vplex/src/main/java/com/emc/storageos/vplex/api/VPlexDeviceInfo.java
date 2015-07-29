/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Info for a VPlex device.
 */
public class VPlexDeviceInfo extends VPlexResourceInfo {

    // The extent information for the device.
    private List<VPlexExtentInfo> extentInfoList = new ArrayList<VPlexExtentInfo>();

    // The child device information for this device. Note that a device
    // can be composed of extents and/or other devices.
    private List<VPlexDeviceInfo> childDeviceInfoList = new ArrayList<VPlexDeviceInfo>();

    // The cluster id.
    private String clusterId = null;

    /**
     * Getter for the extent info for the device.
     * 
     * @return The extent info for the device.
     */
    public List<VPlexExtentInfo> getExtentInfo() {
        return extentInfoList;
    }

    /**
     * Setter for the extent info for the device.
     * 
     * @param infoList The extent info for the device.
     */
    public void setExtentInfo(List<VPlexExtentInfo> infoList) {
        extentInfoList = infoList;
    }

    /**
     * Getter for the child device info for the device.
     * 
     * @return The child device info for the device.
     */
    public List<VPlexDeviceInfo> getChildDeviceInfo() {
        return childDeviceInfoList;
    }

    /**
     * Setter for the child device info for the device.
     * 
     * @param infoList The child device info for the device.
     */
    public void setChildDeviceInfo(List<VPlexDeviceInfo> infoList) {
        childDeviceInfoList = infoList;
    }

    /**
     * Getter for the device cluster id.
     * 
     * @return The device cluster id.
     */
    public String getClusterId() {
        return clusterId;
    }

    /**
     * Setter for the device cluster id.
     * 
     * @param id The device cluster id.
     */
    public void setClusterId(String id) {
        clusterId = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("DeviceInfo ( ");
        str.append(super.toString());
        str.append(", clusterId: " + clusterId);
        for (VPlexExtentInfo extentInfo : extentInfoList) {
            str.append(", ");
            str.append(extentInfo.toString());
        }
        for (VPlexDeviceInfo childDeviceInfo : childDeviceInfoList) {
            str.append(", ");
            str.append(childDeviceInfo.toString());
        }
        str.append(" )");
        return str.toString();
    }
}
