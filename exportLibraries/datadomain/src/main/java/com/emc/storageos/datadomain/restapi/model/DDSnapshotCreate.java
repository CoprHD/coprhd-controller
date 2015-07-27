/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;

@JsonRootName(value="snapshot_create")
public class DDSnapshotCreate {
	
	private String name;
	
	private String path;
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public DDSnapshotCreate(String name, String path) {
		this.name = name;
		this.path = path;
	}

	public String toString() {
        return new Gson().toJson(this).toString();
    }

}
