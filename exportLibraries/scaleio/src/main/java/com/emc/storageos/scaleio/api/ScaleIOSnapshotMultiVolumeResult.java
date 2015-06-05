/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.scaleio.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScaleIOSnapshotMultiVolumeResult {
    private String errorString;
    private boolean isSuccess;
    private String consistencyGroupId;
    private Map<String, ScaleIOSnapshotVolumeResult> results = new HashMap<>();

    public void setErrorString(String errorString) {
        this.errorString = errorString;
    }

    public String getErrorString() {
        return errorString;
    }

    public void setIsSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public String getConsistencyGroupId() {
        return consistencyGroupId;
    }

    public void setConsistencyGroupId(String consistencyGroupId) {
        this.consistencyGroupId = consistencyGroupId;
    }

    public Collection<ScaleIOSnapshotVolumeResult> getResults() {
        return results.values();
    }

    public void addResult(ScaleIOSnapshotVolumeResult result) {
        results.put(result.getName(), result);
    }

    public ScaleIOSnapshotVolumeResult findResult(String snapshotName) {
        return results.get(snapshotName);
    }
}
