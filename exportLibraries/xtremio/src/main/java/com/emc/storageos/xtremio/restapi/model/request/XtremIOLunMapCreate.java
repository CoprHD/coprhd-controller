/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class XtremIOLunMapCreate {

    @SerializedName("vol-id")
    @JsonProperty(value = "vol-id")
    private String name;

    @SerializedName("lun")
    @JsonProperty(value = "lun")
    private String hlu;

    @SerializedName("ig-id")
    @JsonProperty(value = "ig-id")
    private String initiatorGroupName;
    
    @SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
    private String clusterName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHlu() {
        return hlu;
    }

    public void setHlu(String hlu) {
        this.hlu = hlu;
    }

    public String getInitiatorGroupName() {
        return initiatorGroupName;
    }

    public void setInitiatorGroupName(String initiatorGroupName) {
        this.initiatorGroupName = initiatorGroupName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    @Override
    public String toString() {
        return "XtremIOLunMapCreate [name=" + name + ", hlu=" + hlu + ", initiatorGroupName=" + initiatorGroupName + ", clusterName="
                + clusterName + "]";
    }
}
