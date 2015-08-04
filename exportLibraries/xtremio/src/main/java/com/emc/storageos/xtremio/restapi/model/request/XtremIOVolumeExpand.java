/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_volume_expand")
public class XtremIOVolumeExpand {

    @SerializedName("vol-size")
    @JsonProperty(value = "vol-size")
    private String size;
    
    @SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
    private String clusterName;

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    @Override
    public String toString() {
        return "XtremIOVolumeExpand [size=" + size + ", clusterName=" + clusterName + "]";
    }
}
