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
 **/
package com.emc.storageos.recoverpoint.responses;

public class RecoverPointVolumeProtectionInfo {

    public enum volumeProtectionStatus {
        NOT_PROTECTED,
        PROTECTED_SOURCE,
        PROTECTED_TARGET,
        SOURCE_JOURNAL,
        TARGET_JOURNAL
    }

    private volumeProtectionStatus _rpVolumeCurrentProtectionStatus;
    private String _rpProtectionName;
    private long _rpVolumeGroupID;
    private long _rpVolumeSiteID;
    private int _rpVolumeGroupCopyID;
    private long _rpVolumeRSetID;
    private String _rpVolumeWWN;
    private boolean isMetroPoint = false;

    public RecoverPointVolumeProtectionInfo() {
        _rpVolumeCurrentProtectionStatus = volumeProtectionStatus.NOT_PROTECTED;
        _rpVolumeGroupID = -1;
        _rpVolumeSiteID = -1;
        _rpVolumeGroupCopyID = -1;
        _rpVolumeRSetID = -1;
    }

    public volumeProtectionStatus getRpVolumeCurrentProtectionStatus() {
        return _rpVolumeCurrentProtectionStatus;
    }

    public void setRpVolumeCurrentProtectionStatus(
            volumeProtectionStatus rpVolumeCurrentProtectionStatus) {
        this._rpVolumeCurrentProtectionStatus = rpVolumeCurrentProtectionStatus;
    }

    public long getRpVolumeGroupID() {
        return _rpVolumeGroupID;
    }

    public void setRpVolumeGroupID(long rpVolumeGroupID) {
        this._rpVolumeGroupID = rpVolumeGroupID;
    }

    public long getRpVolumeSiteID() {
        return _rpVolumeSiteID;
    }

    public void setRpVolumeSiteID(long rpVolumeSiteID) {
        this._rpVolumeSiteID = rpVolumeSiteID;
    }

    public int getRpVolumeGroupCopyID() {
        return _rpVolumeGroupCopyID;
    }

    public void setRpVolumeGroupCopyID(int rpVolumeGroupCopyID) {
        this._rpVolumeGroupCopyID = rpVolumeGroupCopyID;
    }

    public String getRpProtectionName() {
        return _rpProtectionName;
    }

    public void setRpProtectionName(String rpProtectionName) {
        this._rpProtectionName = rpProtectionName;
    }

    public long getRpVolumeRSetID() {
        return _rpVolumeRSetID;
    }

    public void setRpVolumeRSetID(long rpVolumeRSetID) {
        this._rpVolumeRSetID = rpVolumeRSetID;
    }

    public String getRpVolumeWWN() {
        return _rpVolumeWWN;
    }

    public void setRpVolumeWWN(String _rpVolumeWWN) {
        this._rpVolumeWWN = _rpVolumeWWN;
    }

    public boolean isMetroPoint() {
        return isMetroPoint;
    }

    public void setMetroPoint(boolean isMetroPoint) {
        this.isMetroPoint = isMetroPoint;
    }
}
