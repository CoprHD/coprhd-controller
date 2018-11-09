/*
 * Copyright 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class XtremIOEntityTagDelete {
    @SerializedName("cluster-id")
    @JsonProperty(value = "cluster-id")
    private String clusterId;

    @SerializedName("entity")
    @JsonProperty(value = "entity")
    private String entityType;

    @SerializedName("entity-details")
    @JsonProperty(value = "entity-details")
    private String entityDetails;

    /**
     * @return the clusterId
     */
    public String getClusterId() {
        return clusterId;
    }

    /**
     * @param clusterId the clusterId to set
     */
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    /**
     * @return the entityType
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * @param entityType the entityType to set
     */
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    /**
     * @return the entityDetails
     */
    public String getEntityDetails() {
        return entityDetails;
    }

    /**
     * @param entityDetails the entityDetails to set
     */
    public void setEntityDetails(String entityDetails) {
        this.entityDetails = entityDetails;
    }

    @Override
    public String toString() {
        return "XtremIOEntityTagDelete [clusterId=" + clusterId
                + ", entity=" + entityType
                + ", entity-details=" + entityDetails
                + "]";
    }

}
