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

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value="share_modify")
public class DDShareModify {
	
	// disable: true: disable share; false: enable share
	private Boolean disable;
	
	@SerializedName("max_connections")
	@JsonProperty(value="max_connections")
	private int maxConnections;
	
	// browsing: true: enabled; false: disabled
	private Boolean browsing;

	// writeable: true: enabled; false: disabled
	private Boolean writeable;
	
	private String comment;

	// update clients list, each element contains one client config
	private List<DDShareListModify> clients;
	
	// update users list, each element contains one user config
	private List<DDShareListModify> users;

	// update groups list, each element contains one group config
	private List<DDShareListModify> groups;
	
	public DDShareModify(String comment) {
		this.comment = comment;
	}

	public Boolean getDisable() {
		return disable;
	}

	public void setDisable(Boolean disable) {
		this.disable = disable;
	}

	public int getMaxConnections() {
		return maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public Boolean getBrowsing() {
		return browsing;
	}

	public void setBrowsing(Boolean browsing) {
		this.browsing = browsing;
	}

	public Boolean getWriteable() {
		return writeable;
	}

	public void setWriteable(Boolean writeable) {
		this.writeable = writeable;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public List<DDShareListModify> getClients() {
		return clients;
	}

	public void setClients(List<DDShareListModify> clients) {
		this.clients = clients;
	}

	public List<DDShareListModify> getUsers() {
		return users;
	}

	public void setUsers(List<DDShareListModify> users) {
		this.users = users;
	}

	public List<DDShareListModify> getGroups() {
		return groups;
	}

	public void setGroups(List<DDShareListModify> groups) {
		this.groups = groups;
	}

	public String toString() {
		return new Gson().toJson(this).toString();
	}

}
