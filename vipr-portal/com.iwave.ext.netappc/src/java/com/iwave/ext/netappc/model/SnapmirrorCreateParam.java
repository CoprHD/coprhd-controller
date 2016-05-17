/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc.model;

public class SnapmirrorCreateParam extends SnapmirrorInfo {

    public SnapmirrorCreateParam(SnapmirrorInfo snapMirrorInfo) {
        super(snapMirrorInfo);
    }

    public String getCronScheduleName() {
        return cronScheduleName;
    }

    public void setCronScheduleName(String cronScheduleName) {
        this.cronScheduleName = cronScheduleName;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public boolean isReturnRecord() {
        return returnRecord;
    }

    public void setReturnRecord(boolean returnRecord) {
        this.returnRecord = returnRecord;
    }

    public SnapmirrorCreateParam() {
        // TODO Auto-generated constructor stub
    }

    private String cronScheduleName;
    private String relationshipType = "data_protection";
    private boolean returnRecord = false;

}
