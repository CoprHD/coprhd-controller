/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.vplex.api.VPlexDirectorInfo.DirectorAttribute;

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
    private String cluster = null;

    // The device geometry (RAID level).
    private String geometry = null;

    // The device slot number.
    private String slotNumber = null;
    
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
    public String getCluster() {
        return cluster;
    }

    /**
     * Setter for the device cluster id.
     * 
     * @param id The device cluster id.
     */
    public void setCluster(String id) {
        cluster = id;
    }

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
     * Getter for the device slot number.
     * 
     * @return The device slot number.
     */
    public String getSlotNumber() {
        return slotNumber;
    }

    /**
     * Setter for the device slot number.
     * 
     * @param id The device slot number.
     */
    public void setSlotNumber(String slotNumber) {
        this.slotNumber = slotNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("DeviceInfo ( ");
        str.append(super.toString());
        str.append(", cluster: " + cluster);
        str.append(", geometry: " + geometry);
        str.append(", slotNumber: " + slotNumber);
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
