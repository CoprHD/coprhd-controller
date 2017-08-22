/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.migration;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "devicePairs")
public class DevicePairModel {
    @SerializedName("srcVolumeName")
    @JsonProperty(value = "srcVolumeName")
    private String srcVolumeName;

    @SerializedName("invalidSrc")
    @JsonProperty(value = "invalidSrc")
    private boolean invalidSrc;

    @SerializedName("missingSrc")
    @JsonProperty(value = "missingSrc")
    private boolean missingSrc;

    @SerializedName("tgtVolumeName")
    @JsonProperty(value = "tgtVolumeName")
    private String tgtVolumeName;

    @SerializedName("invalidTgt")
    @JsonProperty(value = "invalidTgt")
    private boolean invalidTgt;

    @SerializedName("missingTgt")
    @JsonProperty(value = "missingTgt")
    private boolean missingTgt;

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

    public String getSrcVolumeName() {
        return srcVolumeName;
    }

    public void setSrcVolumeName(String srcVolumeName) {
        this.srcVolumeName = srcVolumeName;
    }

    public boolean isInvalidSrc() {
        return invalidSrc;
    }

    public void setInvalidSrc(boolean invalidSrc) {
        this.invalidSrc = invalidSrc;
    }

    public boolean isMissingSrc() {
        return missingSrc;
    }

    public void setMissingSrc(boolean missingSrc) {
        this.missingSrc = missingSrc;
    }

    public String getTgtVolumeName() {
        return tgtVolumeName;
    }

    public void setTgtVolumeName(String tgtVolumeName) {
        this.tgtVolumeName = tgtVolumeName;
    }

    public boolean isInvalidTgt() {
        return invalidTgt;
    }

    public void setInvalidTgt(boolean invalidTgt) {
        this.invalidTgt = invalidTgt;
    }

    public boolean isMissingTgt() {
        return missingTgt;
    }

    public void setMissingTgt(boolean missingTgt) {
        this.missingTgt = missingTgt;
    }
}
