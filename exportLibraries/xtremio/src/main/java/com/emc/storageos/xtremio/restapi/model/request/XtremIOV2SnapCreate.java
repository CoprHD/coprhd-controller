package com.emc.storageos.xtremio.restapi.model.request;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class XtremIOV2SnapCreate {
	
	@SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
	private String clusterId;
	
	@SerializedName("consistency-group-id")
    @JsonProperty(value = "consistency-group-id")
	private String consistencyGroupId;
	
	@SerializedName("snap-suffix")
    @JsonProperty(value = "snap-suffix")
	private String snapSuffix;
	
	@SerializedName("snapshot-set-id")
    @JsonProperty(value = "snapshot-set-id")
	private String snapshotSetId;
	
	@SerializedName("snapshot-set-name")
    @JsonProperty(value = "snapshot-set-name")
	private String snapshotSetName;
	
	@SerializedName("snapshot-type")
    @JsonProperty(value = "snapshot-type")
	private String snapshotType;
	
	@SerializedName("tag-list")
    @JsonProperty(value = "tag-list")
	private List<String> tagList;
	
	@SerializedName("volume-list")
    @JsonProperty(value = "volume-list")
	private List<String> volumeList;

	public String getClusterId() {
		return clusterId;
	}

	public void setClusterId(String clusterId) {
		this.clusterId = clusterId;
	}

	public String getConsistencyGroupId() {
		return consistencyGroupId;
	}

	public void setConsistencyGroupId(String consistencyGroupId) {
		this.consistencyGroupId = consistencyGroupId;
	}

	public String getSnapSuffix() {
		return snapSuffix;
	}

	public void setSnapSuffix(String snapSuffix) {
		this.snapSuffix = snapSuffix;
	}

	public String getSnapshotSetId() {
		return snapshotSetId;
	}

	public void setSnapshotSetId(String snapshotSetId) {
		this.snapshotSetId = snapshotSetId;
	}

	public String getSnapshotSetName() {
		return snapshotSetName;
	}

	public void setSnapshotSetName(String snapshotSetName) {
		this.snapshotSetName = snapshotSetName;
	}

	public String getSnapshotType() {
		return snapshotType;
	}

	public void setSnapshotType(String snapshotType) {
		this.snapshotType = snapshotType;
	}

	public List<String> getTagList() {
		return tagList;
	}

	public void setTagList(List<String> tagList) {
		this.tagList = tagList;
	}

	public List<String> getVolumeList() {
		return volumeList;
	}

	public void setVolumeList(List<String> volumeList) {
		this.volumeList = volumeList;
	}

	@Override
	public String toString() {
		return "XtremIOV2SnapCreate [clusterId=" + clusterId
				+ ", consistencyGroupId=" + consistencyGroupId
				+ ", snapSuffix=" + snapSuffix + ", snapshotSetId="
				+ snapshotSetId + ", snapshotSetName=" + snapshotSetName
				+ ", snapshotType=" + snapshotType + ", tagList=" + tagList
				+ ", volumeList=" + volumeList + "]";
	}

}
