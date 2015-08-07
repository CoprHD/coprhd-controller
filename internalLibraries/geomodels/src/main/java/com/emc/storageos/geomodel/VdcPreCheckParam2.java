/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geomodel;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class VdcPreCheckParam2 {
    private VdcConfig.ConfigChangeType configChangeType;
    private List<URI> vdcIds = new ArrayList<>();
    private List<String> blackList = new ArrayList<>();
    private List<String> whiteList = new ArrayList<>();
    private boolean precheckFailed = false;
    private String defaultVdcState;
    private boolean isAllNotReachable;

    @XmlElement(name = "vdcIds")
    public List<URI> getVdcIds() {
        if (vdcIds == null) {
            vdcIds = new ArrayList<>();
        }
        return vdcIds;
    }

    public void setVdcIds(List<URI> vdcIds) {
        this.vdcIds = vdcIds;
    }

    @XmlElement(name = "config_change_type")
    public VdcConfig.ConfigChangeType getConfigChangeType() {
        return configChangeType;
    }

    public void setConfigChangeType(VdcConfig.ConfigChangeType type) {
        configChangeType = type;
    }

    @XmlElement(name = "precheck_failed")
    public boolean isPrecheckFailed() {
        return precheckFailed;
    }

    public void setPrecheckFailed(boolean precheckFailed) {
        this.precheckFailed = precheckFailed;
    }

    @XmlElement(name = "default_vdc_state")
    public String getDefaultVdcState() {
        return defaultVdcState;
    }

    public void setDefaultVdcState(String defaultVdcState) {
        this.defaultVdcState = defaultVdcState;
    }

    @XmlElement(name = "blacklist")
    public List<String> getBlackList() {
        if (blackList == null) {
            blackList = new ArrayList<>();
        }
        return blackList;
    }

    public void setBlackList(List<String> blackList) {
        this.blackList = blackList;
    }

    @XmlElement(name = "whitelist")
    public List<String> getWhiteList() {
        return whiteList;
    }

    public void setWhiteList(List<String> whiteList) {
        this.whiteList = whiteList;
    }

    @XmlElement(name = "isAllReachable")
    public boolean getIsAllNotReachable() {
        return isAllNotReachable;
    }

    public void setIsAllNotReachable(boolean isAllNotReachable) {
        this.isAllNotReachable = isAllNotReachable;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(VdcPreCheckParam2.class.toString());
        builder.append(":\n");

        builder.append("ConfigChangeType: ");
        builder.append(configChangeType);
        builder.append("\n");

        builder.append("vdcIds: ");
        builder.append(vdcIds);
        builder.append("\n");

        builder.append("blackList: ");
        builder.append(blackList);
        builder.append("\n");

        builder.append("whiteList: ");
        builder.append(whiteList);
        builder.append("\n");

        builder.append("precheckFailed: ");
        builder.append(precheckFailed);
        builder.append("\n");

        return builder.toString();
    }
}
