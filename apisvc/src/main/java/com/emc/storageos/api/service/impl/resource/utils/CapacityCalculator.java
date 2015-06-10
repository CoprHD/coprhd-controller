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

public interface CapacityCalculator {
	/**
	 * Calculates the actual allocated capacity on the storage system
	 * for the given requested capacity.
	 * 
	 * @param requestedCapacity the user requested volume capacity
	 * @return the actually array allocated capacity
	 */
	public Long calculateAllocatedCapacity(Long requestedCapacity);
	
	/**
	 * Determines if the requested capacity between the storage system
	 * passed in and the one invoking this method can match.
	 *  
	 * @param storageSystemType
	 * @return Boolean indicating if they can match
	 */
	public Boolean capacitiesCanMatch(String storageSystemType);
}
