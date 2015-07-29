/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value = "mtree")
public class DDMTreeInfo {

    private String id;

    private String name;

    // delete status: 0: not deleted; 1: deleted
    // @SerializedName("del_status")
    // @JsonProperty(value="del_status")
    // private Integer delStatus;

    private DDRestLinkRep link;

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

    // public Integer getDelStatus() {
    // return delStatus;
    // }
    //
    // public void setDelStatus(Integer delStatus) {
    // this.delStatus = delStatus;
    // }

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
