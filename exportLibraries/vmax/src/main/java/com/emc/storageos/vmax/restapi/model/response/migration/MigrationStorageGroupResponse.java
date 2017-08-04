/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.migration;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "storageGroup")
public class MigrationStorageGroupResponse {
    @SerializedName("sourceArray")
    @JsonProperty(value = "sourceArray")
    private String sourceArray;

    @SerializedName("targetArray")
    @JsonProperty(value = "targetArray")
    private String targetArray;

    @SerializedName("storageGroup")
    @JsonProperty(value = "storageGroup")
    private String storageGroup;

    @SerializedName("state")
    @JsonProperty(value = "state")
    private String state;

    @SerializedName("totalCapacity")
    @JsonProperty(value = "totalCapacity")
    private float totalCapacity;

    @SerializedName("remainingCapacity")
    @JsonProperty(value = "remainingCapacity")
    private float remainingCapacity;

    @SerializedName("devicePairs")
    @JsonProperty(value = "devicePairs")
    private List<DevicePairModel> devicePairs;

    @SerializedName("sourceMaskingView")
    @JsonProperty(value = "sourceMaskingView")
    private List<MaskingViewModel> sourceMaskingViewList;

    @SerializedName("targetMaskingView")
    @JsonProperty(value = "targetMaskingView")
    private List<MaskingViewModel> targetMaskingViewList;

    public String toString() {
        return new Gson().toJson(this).toString();
    }

    public String getSourceArray() {
        return sourceArray;
    }

    public void setSourceArray(String sourceArray) {
        this.sourceArray = sourceArray;
    }

    public String getTargetArray() {
        return targetArray;
    }

    public void setTargetArray(String targetArray) {
        this.targetArray = targetArray;
    }

    public String getStorageGroup() {
        return storageGroup;
    }

    public void setStorageGroup(String storageGroup) {
        this.storageGroup = storageGroup;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public float getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(float totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public float getRemainingCapacity() {
        return remainingCapacity;
    }

    public void setRemainingCapacity(float remainingCapacity) {
        this.remainingCapacity = remainingCapacity;
    }

    public List<DevicePairModel> getDevicePairs() {
        return devicePairs;
    }

    public void setDevicePairs(List<DevicePairModel> devicePairs) {
        this.devicePairs = devicePairs;
    }

    public List<MaskingViewModel> getSourceMaskingViewList() {
        return sourceMaskingViewList;
    }

    public void setSourceMaskingViewList(List<MaskingViewModel> sourceMaskingViewList) {
        this.sourceMaskingViewList = sourceMaskingViewList;
    }

    public List<MaskingViewModel> getTargetMaskingViewList() {
        return targetMaskingViewList;
    }

    public void setTargetMaskingViewList(List<MaskingViewModel> targetMaskingViewList) {
        this.targetMaskingViewList = targetMaskingViewList;
    }

}
