/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.ecs.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * ECS specific storage pool
 *
 */
public class ECSStoragePool {
	private String name;
	private String id;
	private Long TotalCapacity;
	private Long FreeCapacity;
	private List<String> storagePoolVDC = new ArrayList<String>();
	private int totalDataCenters;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Long getTotalCapacity() {
		return TotalCapacity;
	}

	public void setTotalCapacity(Long TotalCapacity) {
		this.TotalCapacity = TotalCapacity;
	}

	public Long getFreeCapacity() {
		return FreeCapacity;
	}

	public void setFreeCapacity(Long FreeCapacity) {
		this.FreeCapacity = FreeCapacity;
	}

	public void setStoragePoolVDC(String storagePoolVDC) {
		//multiple ecs storage pools could be present in the same VDC
		if (!this.storagePoolVDC.contains(storagePoolVDC)) {
			this.storagePoolVDC.add(storagePoolVDC);
		}
	}

	public void setTotalDataCenters() {
		this.totalDataCenters = storagePoolVDC.size();
	}

	public int getTotalDataCenters() {
		return totalDataCenters;
	}
}
