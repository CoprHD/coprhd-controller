/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.systems.StorageSystemRestRep;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StorageSystemTypeFilter extends DefaultResourceFilter<StorageSystemRestRep> {

	private Set<String> types;

	public StorageSystemTypeFilter(Set<String> types) {
		this.types = types;
	}

	public StorageSystemTypeFilter(String... types) {
		this.types = new HashSet<String>();
		for (String type : types) {
			this.types.add(type);
		}
	}

	@Override
	public boolean accept(StorageSystemRestRep item) {
		return types.contains(item.getSystemType());
	}

	public StorageSystemTypeFilter(List<String> types) {
		this.types = new HashSet<String>();
		for (String type : types) {
			this.types.add(type);
		}
	}

}
