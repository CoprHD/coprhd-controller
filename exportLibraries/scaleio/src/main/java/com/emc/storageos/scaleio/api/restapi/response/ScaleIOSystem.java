/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.scaleio.api.restapi.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.emc.storageos.scaleio.api.ParsePattern;
import com.emc.storageos.scaleio.api.ScaleIOContants;
import com.emc.storageos.scaleio.api.ScaleIOQueryClusterResult;

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
        return primaryMdmActorIpList;
    }

    public void setPrimaryMdmActorIpList(String[] primaryMdmActorIpList) {
        this.primaryMdmActorIpList = primaryMdmActorIpList;
    }

    public String[] getSecondaryMdmActorIpList() {
        return secondaryMdmActorIpList;
    }

    public void setSecondaryMdmActorIpList(String[] secondaryMdmActorIpList) {
        this.secondaryMdmActorIpList = secondaryMdmActorIpList;
    }

    public String[] getTiebreakerMdmIpList() {
        return tiebreakerMdmIpList;
    }

    public void setTiebreakerMdmIpList(String[] tiebreakerMdmIpList) {
        this.tiebreakerMdmIpList = tiebreakerMdmIpList;
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
        ParsePattern parse = new ParsePattern("EMC ScaleIO Version:\\s+[a-zA-Z](.*?)", ScaleIOContants.SCALEIO_VERSION);
        List<String> versions = parse.isMatch(systemVersionName);
        if (versions != null && versions.size() > 0) {
            result = versions.get(0);
        }
        return result;
    }

    public ScaleIOQueryClusterResult toQueryClusterResult() {
        ScaleIOQueryClusterResult result = new ScaleIOQueryClusterResult();
        result.setClusterMode(mdmMode);
        result.setClusterState(mdmClusterState);
        result.setIPs(primaryMdmActorIpList[0], secondaryMdmActorIpList[0], tiebreakerMdmIpList[0]);
        return result;

    }

}
