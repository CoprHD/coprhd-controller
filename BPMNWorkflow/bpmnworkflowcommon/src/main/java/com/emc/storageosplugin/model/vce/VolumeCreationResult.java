package com.emc.storageosplugin.model.vce;

public class VolumeCreationResult extends OperationResult {
	private String volName;
	
	private String volWWN;
	private String volId;
	private String projectId;
	private String neighborhoodId;

	
	public String getVolName() {
		return volName;
	}

	public void setVolName(String volName) {
		this.volName = volName;
	}
	
	
	public String getVolWWN() {
    	return volWWN;
    }

	public void setVolWWN(String volWWN) {
    	this.volWWN = volWWN;
    }

	public String getVolId() {
		return volId;
	}

	public void setVolId(String volId) {
		this.volId = volId;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getNeighborhoodId() {
		return neighborhoodId;
	}

	public void setNeighborhoodId(String neighborhoodId) {
		this.neighborhoodId = neighborhoodId;
	}


	
}
