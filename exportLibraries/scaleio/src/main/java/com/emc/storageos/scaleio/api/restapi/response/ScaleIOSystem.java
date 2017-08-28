/**
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.scaleio.api.restapi.response;

import java.util.Arrays;
import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.emc.storageos.scaleio.api.ParsePattern;
import com.emc.storageos.scaleio.api.ScaleIOConstants;

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
    private String name;
    private String installId;
    private MdmCluster mdmCluster;

    public MdmCluster getMdmCluster() {
		return mdmCluster;
	}

	public void setMdmCluster(MdmCluster mdmCluster) {
		this.mdmCluster = mdmCluster;
	}

	public String getMdmMode() {
        return mdmMode;
    }

    public void setMdmMode(String mdmMode) {
        this.mdmMode = mdmMode;
    }

    public String[] getPrimaryMdmActorIpList() {
        if(null == primaryMdmActorIpList){
            return null;
        }
        return Arrays.copyOf(primaryMdmActorIpList,primaryMdmActorIpList.length);
    }

    public void setPrimaryMdmActorIpList(String[] primaryMdmActorIpList) {
        if(null == primaryMdmActorIpList){
            return;
        }
        this.primaryMdmActorIpList = Arrays.copyOf(primaryMdmActorIpList,primaryMdmActorIpList.length);
    }

    public String[] getSecondaryMdmActorIpList() {
        if(null == secondaryMdmActorIpList){
            return null;
        }
        return Arrays.copyOf(secondaryMdmActorIpList,secondaryMdmActorIpList.length);
    }

    public void setSecondaryMdmActorIpList(String[] secondaryMdmActorIpList) {
        if(null == secondaryMdmActorIpList){
            return;
        }
        this.secondaryMdmActorIpList = Arrays.copyOf(secondaryMdmActorIpList,secondaryMdmActorIpList.length);
    }

    public String[] getTiebreakerMdmIpList() {
        if(null == tiebreakerMdmIpList){
            return null;
        }
        return Arrays.copyOf(tiebreakerMdmIpList,tiebreakerMdmIpList.length);
    }

    public void setTiebreakerMdmIpList(String[] tiebreakerMdmIpList) {
        if(null == tiebreakerMdmIpList){
            return;
        }
        this.tiebreakerMdmIpList = Arrays.copyOf(tiebreakerMdmIpList,tiebreakerMdmIpList.length);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
