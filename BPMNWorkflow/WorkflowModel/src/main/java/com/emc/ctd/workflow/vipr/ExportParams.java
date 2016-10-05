package com.emc.ctd.workflow.vipr;

import java.net.URI;





import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public  class ExportParams  {
    protected URI hostId;

    protected URI virtualArray;

    protected URI project;

    protected List<String> volumeIds;

    protected String volumeId;

    protected List<String> snapshotIds;

    protected Integer hlu;

    protected Integer minPaths;

    protected Integer maxPaths;

    protected Integer pathsPerInitiator;

    
    
    public ExportParams(URI hostId, URI virtualArray, URI project,
			List<String> volumeIds, String volumeId, List<String> snapshotIds,
			Integer hlu, Integer minPaths, Integer maxPaths,
			Integer pathsPerInitiator) {
		super();
		this.hostId = hostId;
		this.virtualArray = virtualArray;
		this.project = project;
		this.volumeIds = volumeIds;
		this.volumeId = volumeId;
		this.snapshotIds = snapshotIds;
		this.hlu = hlu;
		this.minPaths = minPaths;
		this.maxPaths = maxPaths;
		this.pathsPerInitiator = pathsPerInitiator;
	}


	public ExportParams() {
		// TODO Auto-generated constructor stub
	}


	public URI getHostId() {
		return hostId;
	}


	public void setHostId(URI hostId) {
		this.hostId = hostId;
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


	public String getVolumeId() {
		return volumeId;
	}


	public void setVolumeId(String volumeId) {
		this.volumeId = volumeId;
	}


	public List<String> getSnapshotIds() {
		return snapshotIds;
	}


	public void setSnapshotIds(List<String> snapshotIds) {
		this.snapshotIds = snapshotIds;
	}


	public Integer getHlu() {
		return hlu;
	}


	public void setHlu(Integer hlu) {
		this.hlu = hlu;
	}


	public Integer getMinPaths() {
		return minPaths;
	}


	public void setMinPaths(Integer minPaths) {
		this.minPaths = minPaths;
	}


	public Integer getMaxPaths() {
		return maxPaths;
	}


	public void setMaxPaths(Integer maxPaths) {
		this.maxPaths = maxPaths;
	}


	public Integer getPathsPerInitiator() {
		return pathsPerInitiator;
	}


	public void setPathsPerInitiator(Integer pathsPerInitiator) {
		this.pathsPerInitiator = pathsPerInitiator;
	}


	public List<String> getVolumeIds() {
		return volumeIds;
	}
	public void setVolumeIds(List<String> volumeIds) {
		this.volumeIds = volumeIds;
	}


	@Override
    public String toString() {
        return "Virtual Array=" + virtualArray + ", Project=" + project+", VolumeIds=" + volumeIds + ", HostId=" + hostId + ", HLU=" + hlu;
    }

    
	public static void main(String[] args) {
		ExportParams volumeParams = new ExportParams();
		
		Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
		String pretJson = prettyGson.toJson(volumeParams);

		System.out.println("Pretty printing: " + pretJson);
	}



}