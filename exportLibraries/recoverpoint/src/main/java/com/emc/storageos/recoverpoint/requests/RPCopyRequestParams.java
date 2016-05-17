/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

import java.util.Date;

import com.emc.storageos.recoverpoint.responses.RecoverPointVolumeProtectionInfo;

/**
 * RP Copy object model object. This param object is used to restore/failover/enable RP volumes
 *
 */
public class RPCopyRequestParams {

    /**
     * Parameters that must be filled-in in order to recover, enable, disable, failover a bookmark or APIT
     */

    private String bookmarkName;
    private Date apitTime;
    private RecoverPointVolumeProtectionInfo copyVolumeInfo;
    private String imageAccessMode;

    public enum ImageAccessMode {
        DIRECT_ACCESS
    }

    public RPCopyRequestParams() {
        bookmarkName = null;
        apitTime = null;
    }

    public void cloneMe(RPCopyRequestParams clone) {
        setBookmarkName(clone.getBookmarkName());
        setApitTime(clone.getApitTime());
        setCopyVolumeInfo(clone.getCopyVolumeInfo());
    }

    public String getBookmarkName() {
        return bookmarkName;
    }

    public void setBookmarkName(String bookmarkName) {
        this.bookmarkName = bookmarkName;
    }

    public Date getApitTime() {
        return this.apitTime;
    }

    public void setApitTime(Date apitTime) {
        this.apitTime = apitTime;
    }

    public RecoverPointVolumeProtectionInfo getCopyVolumeInfo() {
        return copyVolumeInfo;
    }

    public void setCopyVolumeInfo(RecoverPointVolumeProtectionInfo copyVolumeInfo) {
        this.copyVolumeInfo = copyVolumeInfo;
    }

    public String getImageAccessMode() {
        return imageAccessMode;
    }

    public void setImageAccessMode(String imageAccessMode) {
        this.imageAccessMode = imageAccessMode;
    }

}
