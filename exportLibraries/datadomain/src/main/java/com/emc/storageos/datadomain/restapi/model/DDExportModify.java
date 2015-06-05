/**
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

import java.util.List;

import com.google.gson.Gson;

public class DDExportModify {
	
	private List<DDExportClientModify> clients;

	public List<DDExportClientModify> getClients() {
		return clients;
	}

	public void setClients(List<DDExportClientModify> clients) {
		this.clients = clients;
	}

	public DDExportModify(List<DDExportClientModify> clients) {
		this.clients = clients;
	}

	public String toString() {
		return new Gson().toJson(this).toString();
	}

}
