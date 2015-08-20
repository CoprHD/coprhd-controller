/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;

@JsonRootName(value = "snapshot_create")
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
