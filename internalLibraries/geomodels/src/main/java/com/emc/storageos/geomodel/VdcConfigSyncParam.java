/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
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
    private String ipsecKey;
    private Long vdcConfigVersion;

    public VdcConfigSyncParam() {
    }

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

    @XmlElement(name = "ipsec_key")
    public String getIpsecKey() {
        return ipsecKey;
    }

    public void setIpsecKey(String ipsecKey) {
        this.ipsecKey = ipsecKey;
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

    @XmlElement(name = "vdc_version")
    public Long getVdcConfigVersion() {
        return vdcConfigVersion;
    }

    public void setVdcConfigVersion(Long vdcConfigVersion) {
        this.vdcConfigVersion = vdcConfigVersion;
    }
}
