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

import com.google.gson.Gson;

public class DDShareListModify {
	
	private String name;
	
	// action: false: add, true: delete. Default is add
	private Boolean delete;
	
	public DDShareListModify(String name, Boolean delete) {
		this.name = name;
		this.delete = delete;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Boolean getDelete() {
		return delete;
	}

	public void setDelete(Boolean delete) {
		this.delete = delete;
	}

	public String toString() {
		return new Gson().toJson(this).toString();
	}

}
