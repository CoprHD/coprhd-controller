/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "vdcs")
public class VdcConfigSyncParam {
    private List<VdcConfig> virtualDataCenters;
    private String assignedVdcId;
    private String geoEncryptionKey;
    private String configChangeType;
    
    public VdcConfigSyncParam() {}
    
    public VdcConfigSyncParam(List<VdcConfig> virtualDataCenters) {
        this.virtualDataCenters = virtualDataCenters;
    }

    @XmlElement(name = "vdc")
    public List<VdcConfig> getVirtualDataCenters() {
        if (virtualDataCenters == null) {
            virtualDataCenters = new ArrayList<VdcConfig>();
        }
        return virtualDataCenters;
    }

    public void setVirtualDataCenters(List<VdcConfig> virtualDataCenters) {
        this.virtualDataCenters = virtualDataCenters;
    }

    @XmlElement(name = "assigned_vdc_id")
    public String getAssignedVdcId() {
        return assignedVdcId;
    }

    public void setAssignedVdcId(String assignedVdcId) {
        this.assignedVdcId = assignedVdcId;
    }

    @XmlElement(name = "geo_encryption_key")
    public String getGeoEncryptionKey() {
        return geoEncryptionKey;
    }

    public void setGeoEncryptionKey(String geoEncryptionKey) {
        this.geoEncryptionKey = geoEncryptionKey;
    }

    @XmlElement(name = "config_change_type")
    public String getConfigChangeType() {
        return configChangeType;
    }

    public void setConfigChangeType(String configChangeType) {
        this.configChangeType = configChangeType;
    }

     @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getName());

        builder.append("\n\tassignedVdcId:");
        builder.append(getAssignedVdcId());

        builder.append("\n\tVdcConfigs:");
        builder.append(getVirtualDataCenters());

        builder.append("\n\tgeoEncryptionKey:");
        builder.append(getGeoEncryptionKey());

        builder.append("\n\tconfigChangeType:");
        builder.append(getConfigChangeType());

        return builder.toString();
    }
}
