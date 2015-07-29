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

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.emc.storageos.datadomain.restapi.DataDomainApiConstants;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "share_create")
public class DDShareCreate {

    private String name;

    private String path;

    @SerializedName("max_connections")
    @JsonProperty(value = "max_connections")
    private int maxConnections;

    private Boolean browsing;

    private Boolean writeable;

    private String comment;

    private List<String> clients;

    private List<String> users;

    private List<String> groups;

    public DDShareCreate(String name, String path, int maxUsers,
            String comment, String permissionType, String permission) {
        this.name = name;
        this.path = path;
        this.comment = comment;
        this.maxConnections = maxUsers;
        this.clients = new ArrayList<>();
        this.clients.add("*");
        if (permissionType.equalsIgnoreCase(DataDomainApiConstants.BROWSE_ALLOW)) {
            this.browsing = true;
        } else {
            this.browsing = false;
        }
        if (permission.equalsIgnoreCase(DataDomainApiConstants.SHARE_READ)) {
            this.writeable = false;
        } else {
            this.writeable = true;
        }
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

    public List<String> getClients() {
        return clients;
    }

    public void setClients(List<String> clients) {
        this.clients = clients;
    }

    public List<String> getUsers() {
        return users;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
