/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.requests;

import com.emc.storageos.vnxe.models.DiskGroup;

public class DiskGroupRequests extends KHRequests<DiskGroup> {
	
	private static final String URL = "/api/instances/diskGroup/";

	public DiskGroupRequests(KHClient client, String id) {
		super(client);
		_url = URL + id;
	}
	
	public DiskGroup get() {
		return getDataForOneObject(DiskGroup.class);
	}

}
