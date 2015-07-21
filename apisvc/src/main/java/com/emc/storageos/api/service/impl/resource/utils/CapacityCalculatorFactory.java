/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Factory used for obtaining capacity calculators based on storage
 * system type.  The capacity calculators are spring configured and
 * are used to calculate the actual allocation capacity for a given
 * requested volume size on the array. 
 */
public class CapacityCalculatorFactory {

	@Resource(name="capacityCalculators")
	private Map<String, CapacityCalculator> capacityCalculators =
			new HashMap<String, CapacityCalculator>();

	@Autowired
	DefaultCapacityCalculator defaultCapacityCalculator;
	
	public CapacityCalculator getCapacityCalculator(String systemType) {
		if (capacityCalculators.containsKey(systemType)) {
			return capacityCalculators.get(systemType);
		} 
		defaultCapacityCalculator.setSystemType(systemType);
		return defaultCapacityCalculator;
	}

}
