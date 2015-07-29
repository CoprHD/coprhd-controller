/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "stats_capacity")
public class DDStatsCapacityInfo {

    @SerializedName("collection_epoch")
    @JsonProperty(value = "collection_epoch")
    private long collectionEpoch;

    @SerializedName("physical_capacity")
    @JsonProperty(value = "physical_capacity")
    private DDCapacity physicalCapacity;

    @SerializedName("logical_capacity")
    @JsonProperty(value = "logical_capacity")
    private DDCapacity logicalCapacity;

    @SerializedName("compression_factor")
    @JsonProperty(value = "compression_factor")
    private float compressionFactor;

    @SerializedName("data_written")
    @JsonProperty(value = "data_written")
    private DDDataWritten dataWritten;

    public long getCollectionEpoch() {
        return collectionEpoch;
    }

    public void setCollectionEpoch(long collectionEpoch) {
        this.collectionEpoch = collectionEpoch;
    }

    public DDCapacity getPhysicalCapacity() {
        return physicalCapacity;
    }

    public void setPhysicalCapacity(DDCapacity physicalCapacity) {
        this.physicalCapacity = physicalCapacity;
    }

    public DDCapacity getLogicalCapacity() {
        return logicalCapacity;
    }

    public void setLogicalCapacity(DDCapacity logicalCapacity) {
        this.logicalCapacity = logicalCapacity;
    }

    public float getCompressionFactor() {
        return compressionFactor;
    }

    public void setCompressionFactor(float compressionFactor) {
        this.compressionFactor = compressionFactor;
    }

    public DDDataWritten getDataWritten() {
        return dataWritten;
    }

    public void setDataWritten(DDDataWritten dataWritten) {
        this.dataWritten = dataWritten;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
