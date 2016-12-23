package com.emc.storageos.driver.ibmsvcdriver.api;/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

public class IBMSVCFCMap {

    private String fCMapID;
    private String fCMapName;
    private String sourceVdiskId;
    private String sourceVdiskName;
    private String targetVdiskId;
    private String targetVdiskname;
    private String fcMapStatus;
    private String fcmapProgress;

    public String getfCMapID() {
        return fCMapID;
    }

    public void setfCMapID(String fCMapID) {
        this.fCMapID = fCMapID;
    }

    public String getfCMapName() {
        return fCMapName;
    }

    public void setfCMapName(String fCMapName) {
        this.fCMapName = fCMapName;
    }

    public String getSourceVdiskId() {
        return sourceVdiskId;
    }

    public void setSourceVdiskId(String sourceVdiskId) {
        this.sourceVdiskId = sourceVdiskId;
    }

    public String getSourceVdiskName() {
        return sourceVdiskName;
    }

    public void setSourceVdiskName(String sourceVdiskName) {
        this.sourceVdiskName = sourceVdiskName;
    }

    public String getTargetVdiskId() {
        return targetVdiskId;
    }

    public void setTargetVdiskId(String targetVdiskId) {
        this.targetVdiskId = targetVdiskId;
    }

    public String getTargetVdiskname() {
        return targetVdiskname;
    }

    public void setTargetVdiskname(String targetVdiskname) {
        this.targetVdiskname = targetVdiskname;
    }

    public String getFcMapStatus() {
        return fcMapStatus;
    }

    public void setFcMapStatus(String fcMapStatus) {
        this.fcMapStatus = fcMapStatus;
    }

    public String getFcmapProgress() {
        return fcmapProgress;
    }

    public void setFcmapProgress(String fcmapProgress) {
        this.fcmapProgress = fcmapProgress;
    }

}
