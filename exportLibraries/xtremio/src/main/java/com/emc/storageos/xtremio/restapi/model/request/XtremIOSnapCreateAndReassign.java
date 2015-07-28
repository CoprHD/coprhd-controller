package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class XtremIOSnapCreateAndReassign {
	
	@SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
	private String clusterId;
	
	@SerializedName("backup-snap-suffix")
    @JsonProperty(value = "backup-snap-suffix")
	private String backupSnapSuffix;
	
	@SerializedName("from-consistency-group-id")
    @JsonProperty(value = "from-consistency-group-id")
	private String fromConsistencyGroupId;
	
	@SerializedName("from-snapshot-set-id")
    @JsonProperty(value = "from-snapshot-set-id")
	private String fromSnapshotSetId;
	
	@SerializedName("from-volume-id")
    @JsonProperty(value = "from-volume-id")
	private String fromVolumeId;
	
	@SerializedName("snapshot-set-name")
    @JsonProperty(value = "snapshot-set-name")
	private String snapshotSetName;
	
	@SerializedName("no-backup")
    @JsonProperty(value = "no-backup")
	private String noBackup;
	
	@SerializedName("to-consistency-group-id")
    @JsonProperty(value = "to-consistency-group-id")
	private String toConsistencyGroupId;
	
	@SerializedName("to-snapshot-set-id")
    @JsonProperty(value = "to-snapshot-set-id")
	private String toSnapshotSetId;
	
	@SerializedName("to-volume-id")
    @JsonProperty(value = "to-volume-id")
	private String toVolumeId;

	public String getClusterId() {
		return clusterId;
	}

	public void setClusterId(String clusterId) {
		this.clusterId = clusterId;
	}

	public String getBackupSnapSuffix() {
		return backupSnapSuffix;
	}

	public void setBackupSnapSuffix(String backupSnapSuffix) {
		this.backupSnapSuffix = backupSnapSuffix;
	}

	public String getFromConsistencyGroupId() {
		return fromConsistencyGroupId;
	}

	public void setFromConsistencyGroupId(String fromConsistencyGroupId) {
		this.fromConsistencyGroupId = fromConsistencyGroupId;
	}

	public String getFromSnapshotSetId() {
		return fromSnapshotSetId;
	}

	public void setFromSnapshotSetId(String fromSnapshotSetId) {
		this.fromSnapshotSetId = fromSnapshotSetId;
	}

	public String getFromVolumeId() {
		return fromVolumeId;
	}

	public void setFromVolumeId(String fromVolumeId) {
		this.fromVolumeId = fromVolumeId;
	}

	public String getSnapshotSetName() {
		return snapshotSetName;
	}

	public void setSnapshotSetName(String snapshotSetName) {
		this.snapshotSetName = snapshotSetName;
	}

	public String getNoBackup() {
		return noBackup;
	}

	public void setNoBackup(String noBackup) {
		this.noBackup = noBackup;
	}

	public String getToConsistencyGroupId() {
		return toConsistencyGroupId;
	}

	public void setToConsistencyGroupId(String toConsistencyGroupId) {
		this.toConsistencyGroupId = toConsistencyGroupId;
	}

	public String getToSnapshotSetId() {
		return toSnapshotSetId;
	}

	public void setToSnapshotSetId(String toSnapshotSetId) {
		this.toSnapshotSetId = toSnapshotSetId;
	}

	public String getToVolumeId() {
		return toVolumeId;
	}

	public void setToVolumeId(String toVolumeId) {
		this.toVolumeId = toVolumeId;
	}

	@Override
	public String toString() {
		return "XtremIOSnapCreateAndReassign [clusterId=" + clusterId
				+ ", backupSnapSuffix=" + backupSnapSuffix
				+ ", fromConsistencyGroupId=" + fromConsistencyGroupId
				+ ", fromSnapshotSetId=" + fromSnapshotSetId
				+ ", fromVolumeId=" + fromVolumeId + ", snapshotSetName="
				+ snapshotSetName + ", noBackup=" + noBackup
				+ ", toConsistencyGroupId=" + toConsistencyGroupId
				+ ", toSnapshotSetId=" + toSnapshotSetId + ", toVolumeId="
				+ toVolumeId + "]";
	}

}
