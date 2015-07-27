/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class DDShareInfoDetail {
	
	private String id;
	
	private String name; // minOccurs="0"
	
	private String path; // minOccurs="0"
	
	@SerializedName("mtree_id")
	private String mtreeId; // minOccurs="0"
	
	@SerializedName("mtree_name")
	private String mtreeName; // minOccurs="0"
	
	// Access control entry
	private String aces; // minOccurs="0"
	
	private List<DDKeyValuePair> option; // minOccurs="0" maxOccurs="unbounded"
	
	// -1: error; 0: path (mtree/subdirectory) does not exist; 1: path exists
	@SerializedName("path_status")
    @JsonProperty(value="path_status")
	private int pathStatus; // minOccurs="0"
	
	private List<DDRestLinkRep> link; // minOccurs="0" maxOccurs="unbounded"

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

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

	@JsonProperty(value="mtree_id")
	public String getMtreeId() {
		return mtreeId;
	}

	public void setMtreeId(String mtreeId) {
		this.mtreeId = mtreeId;
	}

	@JsonProperty(value="mtree_name")
	public String getMtreeName() {
		return mtreeName;
	}

	public void setMtreeName(String mtreeName) {
		this.mtreeName = mtreeName;
	}

	public String getAces() {
		return aces;
	}

	public void setAces(String aces) {
		this.aces = aces;
	}

	public List<DDKeyValuePair> getOption() {
		return option;
	}

	public void setOption(List<DDKeyValuePair> option) {
		this.option = option;
	}

	@JsonProperty(value="path_status")
	public int getPathStatus() {
		return pathStatus;
	}

	public void setPathStatus(int pathStatus) {
		this.pathStatus = pathStatus;
	}

	public List<DDRestLinkRep> getLink() {
		return link;
	}

	public void setLink(List<DDRestLinkRep> link) {
		this.link = link;
	}
	
}
