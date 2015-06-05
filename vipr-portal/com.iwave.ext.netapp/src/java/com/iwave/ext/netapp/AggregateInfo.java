/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @author sdorcas
 * Models a subset aggregate information 
 */
public class AggregateInfo implements Serializable {

	private static final long serialVersionUID = -4828311312021506431L;
	
	private String name;
	private int diskCount;
	private String state;
	private String raidStatus;
	private Set<String> diskTypes;
	private Set<String> diskSpeeds;
	private long sizeAvailable;
	private long sizeTotal;
	private long sizeUsed;
	private int volumeCount;
	private List<String> volumes = null;
	
	public AggregateInfo() {}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getDiskCount() {
		return diskCount;
	}
	public void setDiskCount(int diskCount) {
		this.diskCount = diskCount;
	}
	public String getState() {
		return state;
	}
	public void setState(String state) {
		this.state = state;
	}
	public String getRaidStatus() {
		return raidStatus;
	}
	public void setRaidStatus(String raidStatus) {
		this.raidStatus = raidStatus;
	}

    public long getSizeAvailable() {
		return sizeAvailable;
	}
	public void setSizeAvailable(long sizeAvailable) {
		this.sizeAvailable = sizeAvailable;
	}
	public long getSizeTotal() {
		return sizeTotal;
	}
	public void setSizeTotal(long sizeTotal) {
		this.sizeTotal = sizeTotal;
	}
	public long getSizeUsed() {
		return sizeUsed;
	}
	public void setSizeUsed(long sizeUsed) {
		this.sizeUsed = sizeUsed;
	}
	public int getVolumeCount() {
		return volumeCount;
	}
	public void setVolumeCount(int volumeCount) {
		this.volumeCount = volumeCount;
	}
	public List<String> getVolumes() {
		return volumes;
	}
	public void setVolumes(List<String> volumes) {
		this.volumes = volumes;
	}
    
    public Set<String> getDiskTypes() {
        return diskTypes;
    }
    
    public void setDiskTypes(Set<String> diskTypes) {
        this.diskTypes = diskTypes;
    }
    
    public Set<String> getDiskSpeeds() {
        return diskSpeeds;
    }
    
    public void setDiskSpeeds(Set<String> diskSpeeds) {
        this.diskSpeeds = diskSpeeds;
    }
}
