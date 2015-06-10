/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.xtremio.restapi.model.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.common.base.Joiner;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_volume")
public class XtremIOVolume {
    
    @SerializedName("vol-id")
    @JsonProperty(value="vol-id")
    private List<String> volInfo;
    
    @SerializedName("naa-name")
    @JsonProperty(value="naa-name")
    private String wwn;
    
    @SerializedName("vol-size")
    @JsonProperty(value="vol-size")
    private String allocatedCapacity;
    
    @SerializedName("lun-mapping-list")
    @JsonProperty(value="lun-mapping-list")
    private List<List<Object>> lunMaps;
    
    @SerializedName("dest-snap-list")
    @JsonProperty(value="dest-snap-list")
    private List<List<Object>> snaps;
    
    @SerializedName("ancestor-vol-id")
    @JsonProperty(value="ancestor-vol-id")
    private List<String> ancestoVolInfo;

    public List<String> getVolInfo() {
        return volInfo;
    }

    public void setVolInfo(List<String> volInfo) {
        this.volInfo = volInfo;
    }

    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    public String getAllocatedCapacity() {
        return allocatedCapacity;
    }

    public void setAllocatedCapacity(String allocatedCapacity) {
        this.allocatedCapacity = allocatedCapacity;
    }
    
    public List<List<Object>> getLunMaps() {
        return lunMaps;
    }

    public void setLunMaps(List<List<Object>> lunMaps) {
        this.lunMaps = lunMaps;
    }

    public List<List<Object>> getSnaps() {
        return snaps;
    }

    public void setSnaps(List<List<Object>> snaps) {
        this.snaps = snaps;
    }

    public List<String> getAncestoVolInfo() {
        return ancestoVolInfo;
    }

    public void setAncestoVolInfo(List<String> ancestoVolInfo) {
        this.ancestoVolInfo = ancestoVolInfo;
    }

    public String toString() {
        return "Raw response: [vol-id: " + Joiner.on("; ").join(volInfo) +
                "][naa-name: " + wwn +
                "][vol-size: " + allocatedCapacity + "]";
    }
}
