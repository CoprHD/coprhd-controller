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

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import java.util.List;

@JsonRootName(value="nfs_export")
public class DDExportInfoDetail {
	
	private String id;
	
	private String path;

	@SerializedName("mtree_id")
	private String mtreeID;

	@SerializedName("mtree_name")
	private String mtreeName;

	// -1: error; 0: path does not exist; 1: path exists
	@SerializedName("path_status")
	@JsonProperty(value="path_status")
	private int pathStatus;

	private List<DDExportClient> clients;
	
	private List<DDRestLinkRep> link;

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}

    @JsonProperty(value="mtree_id")
	public String getMtreeID() {
		return mtreeID;
	}
	public void setMtreeID(String mtreeID) {
		this.mtreeID = mtreeID;
	}

    @JsonProperty(value="mtree_name")
	public String getMtreeName() {
		return mtreeName;
	}
	public void setMtreeName(String mtreeName) {
		this.mtreeName = mtreeName;
	}

    @JsonProperty(value="path_status")
	public int getPathStatus() {
		return pathStatus;
	}
	public void setPathStatus(int pathStatus) {
		this.pathStatus = pathStatus;
	}

	public List<DDExportClient> getClients() {
		return clients;
	}
	public void setClients(List<DDExportClient> clients) {
		this.clients = clients;
	}
	
	public List<DDRestLinkRep> getLink() {
		return link;
	}
	public void setLink(List<DDRestLinkRep> link) {
		this.link = link;
	}
	public String toString() {
        return new Gson().toJson(this).toString();
    }

}
