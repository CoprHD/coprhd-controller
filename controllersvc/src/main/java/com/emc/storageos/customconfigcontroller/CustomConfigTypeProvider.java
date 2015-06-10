/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.customconfigcontroller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will hold the metadata for custom configuration
 * 
 */
public class CustomConfigTypeProvider {

	private static final Logger logger = LoggerFactory
			.getLogger(CustomConfigTypeProvider.class);

	private List<CustomConfigType> customConfigTypes;

	public List<CustomConfigType> getCustomConfigTypes() {
		return customConfigTypes;
	}

	public void setCustomConfigTypes(List<CustomConfigType> customConfigTypes) {
		this.customConfigTypes = customConfigTypes;
	}

	public CustomConfigType getCustomConfigType(String name) {
		for (CustomConfigType item : customConfigTypes) {
			if (item.getName().equals(name)) {
				return item;
			}
		}
		return null;
	}

}
