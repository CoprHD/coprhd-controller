/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_volume")
public class XtremIOVolume {

    @SerializedName("vol-id")
    @JsonProperty(value = "vol-id")
    private List<String> volInfo;

    @SerializedName("naa-name")
    @JsonProperty(value = "naa-name")
    private String wwn;

    @SerializedName("vol-size")
    @JsonProperty(value = "vol-size")
    private String allocatedCapacity;

    @SerializedName("lun-mapping-list")
    @JsonProperty(value = "lun-mapping-list")
    private List<List<Object>> lunMaps;

    @SerializedName("dest-snap-list")
    @JsonProperty(value = "dest-snap-list")
    private List<List<Object>> snaps;
    
    @SerializedName("related-consistency-groups")
    @JsonProperty(value = "related-consistency-groups")
    private List<List<Object>> consistencyGroups;
    
    @SerializedName("snapset-list")
    @JsonProperty(value = "snapset-list")
    private List<List<Object>> snapSetList;

    @SerializedName("ancestor-vol-id")
    @JsonProperty(value = "ancestor-vol-id")
    private List<String> ancestoVolInfo;

    @SerializedName("snapshot-type")
    @JsonProperty(value = "snapshot-type")
    private String snapshotType;

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
    
    public List<List<Object>> getConsistencyGroups() {
        return consistencyGroups;
    }

    public void setConsistencyGroups(List<List<Object>> consistencyGroups) {
        this.consistencyGroups = consistencyGroups;
    }

    public List<List<Object>> getSnapSetList() {
		return snapSetList;
	}

	public void setSnapSetList(List<List<Object>> snapSetList) {
		this.snapSetList = snapSetList;
	}

	public List<String> getAncestoVolInfo() {
        return ancestoVolInfo;
    }

    public void setAncestoVolInfo(List<String> ancestoVolInfo) {
        this.ancestoVolInfo = ancestoVolInfo;
    }

    public String getSnapshotType() {
        return snapshotType;
    }

    public void setSnapshotType(String snapshotType) {
        this.snapshotType = snapshotType;
    }

    @Override
    public String toString() {
        return "XtremIOVolume [volInfo=" + volInfo + ", wwn=" + wwn + ", allocatedCapacity=" + allocatedCapacity + ", lunMaps=" + lunMaps
                + ", snaps=" + snaps + ", ancestoVolInfo=" + ancestoVolInfo + ", snapshotType=" + snapshotType + ", consistencyGroups=" + consistencyGroups +"]";
    }
}
