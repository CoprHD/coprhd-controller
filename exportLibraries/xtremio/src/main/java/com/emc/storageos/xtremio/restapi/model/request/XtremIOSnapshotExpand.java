/*
 * Copyright (c) 2018 Dell-EMC
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_snapshot_expand")
public class XtremIOSnapshotExpand {

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
        return "XtremIOSnapshotExpand [size=" + size + ", clusterName=" + clusterName + "]";
    }
}
