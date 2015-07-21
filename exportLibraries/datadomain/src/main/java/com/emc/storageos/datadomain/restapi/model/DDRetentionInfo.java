/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.annotations.SerializedName;

public class DDRetentionInfo {
	
    @SerializedName("data_interval")
    @JsonProperty(value="data_interval")
    private DDStatsIntervalQuery dataInterval;
	
    @SerializedName("retention_days")
    @JsonProperty(value="retention_days")
    private long retentionDays;

    public DDStatsIntervalQuery getDataInterval() {
        return dataInterval;
    }

    public void setDataInterval(DDStatsIntervalQuery dataInterval) {
        this.dataInterval = dataInterval; 
    }

    public long getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(long retentionDays) {
        this.retentionDays = retentionDays;
    }

}