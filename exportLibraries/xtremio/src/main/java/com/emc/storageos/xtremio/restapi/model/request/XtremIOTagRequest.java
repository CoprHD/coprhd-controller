/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_tag_request")
public class XtremIOTagRequest {

    @SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
    private String clusterId;

    @SerializedName("entity")
    @JsonProperty(value = "entity")
    private String entity;

    @SerializedName("entity-details")
    @JsonProperty(value = "entity-details")
    private String entityDetails;

    @SerializedName("tag-name")
    @JsonProperty(value = "tag-name")
    private String tagName;

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getEntityDetails() {
        return entityDetails;
    }

    public void setEntityDetails(String entityDetails) {
        this.entityDetails = entityDetails;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    @Override
    public String toString() {
        return "XtremIOTagRequest [clusterId=" + clusterId + ", entity=" + entity + ", entityDetails=" + entityDetails + ", tagName="
                + tagName + "]";
    }
}
