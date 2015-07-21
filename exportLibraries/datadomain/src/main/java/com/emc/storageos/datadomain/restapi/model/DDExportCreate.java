/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value="export_create")
public class DDExportCreate {
	
	// Full path for the export is required
    private String path;

    // At least one client is required
    private List<DDExportClient> clients;

    public String getPath() {
		return path;
    }

    public void setPath(String path) {
    	this.path = path;
    }

    public List<DDExportClient> getClients() {
    	return clients;
    }

    public void setClients(List<DDExportClient> clients) {
    	this.clients = clients;
    }

    public DDExportCreate(String path, List<DDExportClient> clients) {
    	this.path = path;
    	this.clients = clients;
    }

    public String toString() {
    	return new Gson().toJson(this).toString();
    }
    
}
