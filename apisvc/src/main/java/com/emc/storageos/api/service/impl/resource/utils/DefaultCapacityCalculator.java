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

public class DefaultCapacityCalculator implements CapacityCalculator {
	private String systemType = null;
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Long calculateAllocatedCapacity(Long requestedCapacity) {
		return requestedCapacity;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Boolean capacitiesCanMatch(String storageSystemType) {
		if (this.systemType.equalsIgnoreCase(DiscoveredDataObject.Type.vnxblock.toString())) {
			return true;
		}
		return false;
	}	

	public void setSystemType(String systemType) {
		this.systemType = systemType;
	}

}
