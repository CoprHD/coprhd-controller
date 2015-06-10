/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.model.DiscoveredDataObject;

public class VmaxCapacityCalculator implements CapacityCalculator {
	private static final long tracksPerCylinder = 15;
	private static final long blocksPerTrack = 128;
	private static final long bytesPerBlock = 512;
	private static final long bytesPerCylinder = 
			(tracksPerCylinder * blocksPerTrack * bytesPerBlock);
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Long calculateAllocatedCapacity(Long requestedCapacity) {
		if (requestedCapacity != null) {
			long cyls = (long) Math.ceil((double) requestedCapacity / bytesPerCylinder);
			return (cyls * bytesPerCylinder);
		}
		
		return requestedCapacity;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Boolean capacitiesCanMatch(String storageSystemType) {
		if (storageSystemType.equalsIgnoreCase(DiscoveredDataObject.Type.xtremio.name())) {
			return false;
		}
		return true;		
	}
}
