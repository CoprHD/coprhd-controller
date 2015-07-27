/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
