/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value="quota_config")
public class DDQuotaConfig {

    @SerializedName("hard_limit")
    @JsonProperty(value="hard_limit")
    private long hardLimit;

    @SerializedName("soft_limit")
    @JsonProperty(value="soft_limit")
    private long softLimit;

    public long getHardLimit() {
		return hardLimit;
	}
	public void setHardLimit(long hardLimit) {
		this.hardLimit = hardLimit;
	}

	public long getSoftLimit() {
		return softLimit;
	}
	public void setSoftLimit(long softLimit) {
		this.softLimit = softLimit;
	}

    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
