/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class DiskGroup {
	private String id;
	private String name;
	private String emcPartNumber;
	private int tierType;
	private int diskTechnology;
	private long diskSize;
	private long advertisedSize;
	private int rpm;
	private long speed;
	private int totalDisks;
	private int minHotSpareCandidates;
	private int hotSparePolicyStatus;
	private int unconfiguredDisks;
	
	public static enum DiskTechnologyEnum {
		SAS(1),
		NL_SAS(2),
		SAS_FLASH(5),
		SAS_FLASH_VP(6);
		
		private static final Map<Integer, DiskTechnologyEnum> diskTechnologyMap = new HashMap<Integer,DiskTechnologyEnum>();
	    static {
	        for (DiskTechnologyEnum type : DiskTechnologyEnum.values()) {
	            diskTechnologyMap.put(type.value, type);
	        }
	    }
		
		private int value;

	    private DiskTechnologyEnum(int value) {
	        this.value = value;
	    }
	    
	    public int getValue() {
	        return this.value;
	    }
	    
	    public static DiskTechnologyEnum getEnumValue(Integer inValue) {
	    	return diskTechnologyMap.get(inValue);
	    }
	    

	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmcPartNumber() {
		return emcPartNumber;
	}

	public void setEmcPartNumber(String emcPartNumber) {
		this.emcPartNumber = emcPartNumber;
	}

	public int getTierType() {
		return tierType;
	}

	public void setTierType(int tierType) {
		this.tierType = tierType;
	}

	public int getDiskTechnology() {
		return diskTechnology;
	}

	public void setDiskTechnology(int diskTechnology) {
		this.diskTechnology = diskTechnology;
	}
	
	public DiskTechnologyEnum getDiskTechnologyEnum() {
		return DiskTechnologyEnum.getEnumValue(diskTechnology);
	}

	public long getDiskSize() {
		return diskSize;
	}

	public void setDiskSize(long diskSize) {
		this.diskSize = diskSize;
	}

	public long getAdvertisedSize() {
		return advertisedSize;
	}

	public void setAdvertisedSize(long advertisedSize) {
		this.advertisedSize = advertisedSize;
	}

	public int getRpm() {
		return rpm;
	}

	public void setRpm(int rpm) {
		this.rpm = rpm;
	}

	public long getSpeed() {
		return speed;
	}

	public void setSpeed(long speed) {
		this.speed = speed;
	}

	public int getTotalDisks() {
		return totalDisks;
	}

	public void setTotalDisks(int totalDisks) {
		this.totalDisks = totalDisks;
	}

	public int getMinHotSpareCandidates() {
		return minHotSpareCandidates;
	}

	public void setMinHotSpareCandidates(int minHotSpareCandidates) {
		this.minHotSpareCandidates = minHotSpareCandidates;
	}

	public int getHotSparePolicyStatus() {
		return hotSparePolicyStatus;
	}

	public void setHotSparePolicyStatus(int hotSparePolicyStatus) {
		this.hotSparePolicyStatus = hotSparePolicyStatus;
	}

	public int getUnconfiguredDisks() {
		return unconfiguredDisks;
	}

	public void setUnconfiguredDisks(int unconfiguredDisks) {
		this.unconfiguredDisks = unconfiguredDisks;
	}	
}
