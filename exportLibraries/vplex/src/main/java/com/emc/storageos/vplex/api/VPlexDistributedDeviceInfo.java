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
 * Info for a VPlex distributed device.
 */
public class VPlexDistributedDeviceInfo extends VPlexResourceInfo {

    // The local devices which comprise the distributed device.
    List<VPlexDeviceInfo> localDeviceInfoList = new ArrayList<VPlexDeviceInfo>();

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
            return localDeviceInfoList.get(0).getClusterId();
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
        for (VPlexDeviceInfo localDeviceInfo : localDeviceInfoList) {
            str.append(", ");
            str.append(localDeviceInfo.toString());
        }
        str.append(" )");

        return str.toString();
    }
}
