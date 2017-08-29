/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Symmetrix {
    @SerializedName("symmetrixId")
    @JsonProperty(value = "symmetrixId")
    private String symmetrixId;

    @SerializedName("model")
    @JsonProperty(value = "model")
    private String model;

    @SerializedName("ucode")
    @JsonProperty(value = "ucode")
    private String ucode;

    @SerializedName("device_count")
    @JsonProperty(value = "device_count")
    private int deviceCount;

    @SerializedName("local")
    @JsonProperty(value = "local")
    boolean local;

    public String getSymmetrixId() {
        return symmetrixId;
    }

    public void setSymmetrixId(String symmetrixId) {
        this.symmetrixId = symmetrixId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUcode() {
        return ucode;
    }

    public void setUcode(String ucode) {
        this.ucode = ucode;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public void setDeviceCount(int deviceCount) {
        this.deviceCount = deviceCount;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    @Override
    public String toString() {
        return "Symmetrix [symmetrixId=" + symmetrixId + ", model=" + model + ", ucode=" + ucode + ", deviceCount=" + deviceCount
                + ", local=" + local + "]";
    }
}
