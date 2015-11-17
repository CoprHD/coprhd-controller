/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.api.restapi.response;

import java.util.List;

/**
 * Create volume snapshot response
 * 
 */
public class ScaleIOSnapshotVolumeResponse {
    private List<String> volumeIdList;
    private String snapshotGroupId;

    public List<String> getVolumeIdList() {
        return volumeIdList;
    }

    public void setVolumeIdList(List<String> volumeIdList) {
        this.volumeIdList = volumeIdList;
    }

    public String getSnapshotGroupId() {
        return snapshotGroupId;
    }

    public void setSnapshotGroupId(String snapshotGroupId) {
        this.snapshotGroupId = snapshotGroupId;
    }

}
