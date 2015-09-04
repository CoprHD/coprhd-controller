/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "nfs_export")
public class DDExportInfo {

    private String id;

    private String path;

    // -1: error; 0: path does not exist; 1: path exists
    @SerializedName("path_status")
    @JsonProperty(value = "path_status")
    private int pathStatus;

    private DDRestLinkRep link;

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

    public int getPathStatus() {
        return pathStatus;
    }

    public void setPathStatus(int pathStatus) {
        this.pathStatus = pathStatus;
    }

    public DDRestLinkRep getLink() {
        return link;
    }

    public void setLink(DDRestLinkRep link) {
        this.link = link;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
