/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "response_link")
public class XtremIOResponseContent {
    @SerializedName("href")
    @JsonProperty(value = "href")
    private String href;

    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
