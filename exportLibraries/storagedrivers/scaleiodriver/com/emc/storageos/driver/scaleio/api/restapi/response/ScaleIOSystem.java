/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.emc.storageos.driver.scaleio.api.ParsePattern;
import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;

/**
 * System attributes
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaleIOSystem {
    private String mdmMode;
    private String[] primaryMdmActorIpList;
    private String[] secondaryMdmActorIpList;
    private String[] tiebreakerMdmIpList;
    private String systemVersionName;
    private String mdmClusterState;
    private String id;
    private String installId;

    public String getMdmMode() {
        return mdmMode;
    }

    public void setMdmMode(String mdmMode) {
        this.mdmMode = mdmMode;
    }

    public String[] getPrimaryMdmActorIpList() {
        return primaryMdmActorIpList.clone();
    }

    public void setPrimaryMdmActorIpList(String[] primaryMdmActorIpList) {
        this.primaryMdmActorIpList = primaryMdmActorIpList.clone();
    }

    public String[] getSecondaryMdmActorIpList() {
        return secondaryMdmActorIpList.clone();
    }

    public void setSecondaryMdmActorIpList(String[] secondaryMdmActorIpList) {
        this.secondaryMdmActorIpList = secondaryMdmActorIpList.clone();
    }

    public String[] getTiebreakerMdmIpList() {
        return tiebreakerMdmIpList.clone();
    }

    public void setTiebreakerMdmIpList(String[] tiebreakerMdmIpList) {
        this.tiebreakerMdmIpList = tiebreakerMdmIpList.clone();
    }

    public String getSystemVersionName() {
        return systemVersionName;
    }

    public void setSystemVersionName(String systemVersionName) {
        this.systemVersionName = systemVersionName;
    }

    public String getMdmClusterState() {
        return mdmClusterState;
    }

    public void setMdmClusterState(String mdmClusterState) {
        this.mdmClusterState = mdmClusterState;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInstallId() {
        return installId;
    }

    public void setInstallId(String installId) {
        this.installId = installId;
    }

    public String getVersion() {
        String result = null;
        ParsePattern parse = new ParsePattern("EMC ScaleIO Version:\\s+[a-zA-Z](.*?)", ScaleIOConstants.SCALEIO_VERSION);
        List<String> versions = parse.isMatch(systemVersionName);
        if (versions != null && !versions.isEmpty()) {
            result = versions.get(0);
        }
        return result;
    }

}
