package com.emc.ctd.workflow.vipr;

import java.net.URI;

import com.emc.ctd.vipr.api.CommonUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class VolumeParams {

	private String hostId = "host";
	private Integer hlu = -1;

	protected URI virtualPool;

	protected URI virtualArray;

	protected URI project;

	protected String volumeName;

	protected String volumeSize;

	protected Integer count;

	protected URI consistencyGroup;

	// return new VolumeParams(CommonUtils.uri(varrayID),
	// CommonUtils.uri(projectID), CommonUtils.uri(vpoolID),
	// volumeName, volumeSize, Integer.parseInt(count),
	// CommonUtils.uri(consistencyGroupId));

	public VolumeParams(URI virtualArray, URI project, URI virtualPool,
			String volumeName, String volumeSize, int count,
			URI consistencyGroup) {

		this.virtualPool = virtualPool;
		this.virtualArray = virtualArray;
		this.project = project;
		this.volumeName=volumeName;
		this.volumeSize = volumeSize;
		this.consistencyGroup = consistencyGroup;
		this.count = count;

	}

	public VolumeParams() {
		// TODO Auto-generated constructor stub
	}

	public String getHostId() {
		return hostId;
	}

	public void setHostId(String hostId) {
		this.hostId = hostId;
	}

	public Integer getHlu() {
		return hlu;
	}

	public void setHlu(Integer hlu) {
		this.hlu = hlu;
	}

	public URI getVirtualPool() {
		return virtualPool;
	}

	public void setVirtualPool(URI virtualPool) {
		this.virtualPool = virtualPool;
	}

	public URI getVirtualArray() {
		return virtualArray;
	}

	public void setVirtualArray(URI virtualArray) {
		this.virtualArray = virtualArray;
	}

	public URI getProject() {
		return project;
	}

	public void setProject(URI project) {
		this.project = project;
	}

	public String getVolumeName() {
		return volumeName;
	}

	public void setVolumeName(String volumeName) {
		this.volumeName = volumeName;
	}

	public String getVolumeSize() {
		return volumeSize;
	}

	public void setVolumeSize(String volumeSize) {
		this.volumeSize = volumeSize;
	}

	public Integer getCount() {
		return count;
	}

	public void setCount(Integer count) {
		this.count = count;
	}

	public URI getConsistencyGroup() {
		return consistencyGroup;
	}

	public void setConsistencyGroup(URI consistencyGroup) {
		this.consistencyGroup = consistencyGroup;
	}

	@Override
	public String toString() {
		return "Virtual Pool=" + virtualPool + ", Virtual Array="
				+ virtualArray + ", Project=" + project
				+ ", Consistency Group=" + consistencyGroup;
	}

	public static void main(String[] args) {
		VolumeParams volumeParams = new VolumeParams();

		Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
		String pretJson = prettyGson.toJson(volumeParams);

		System.out.println("Pretty printing: " + pretJson);
	}
}