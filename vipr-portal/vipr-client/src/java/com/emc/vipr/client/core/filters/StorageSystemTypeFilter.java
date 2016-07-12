/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.model.systems.StorageSystemRestRep;

public class StorageSystemTypeFilter extends DefaultResourceFilter<StorageSystemRestRep> {

    public static final StorageSystemTypeFilter BLOCK = new StorageSystemTypeFilter("vnxblock", "vmax", "vplex", "hds", "xtremio", "vnxe", "unity",
            "ibmxiv", "ceph");
    public static final StorageSystemTypeFilter FILE = new StorageSystemTypeFilter("isilon", "vnxfile", "netapp", "datadomain", "vnxe", "unity",
            "netappc");

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
