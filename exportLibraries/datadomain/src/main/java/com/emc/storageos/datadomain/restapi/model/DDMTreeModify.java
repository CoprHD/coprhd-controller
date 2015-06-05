/**
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

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value="mtree_modify")
public class DDMTreeModify {
	
	@SerializedName("tenant_unit")
    @JsonProperty(value="tenant_unit")
	public String tenantUnit; // Optional
	
	@SerializedName("quota_config")
    @JsonProperty(value="quota_config")
	public DDQuotaConfig quotaConfig;  // Optional
	
	public DDRetentionLockSet retention;
	
	public DDMTreeModify(DDQuotaConfig quotaConfig,
			DDRetentionLockSet retention) {
		this.quotaConfig = quotaConfig;
		this.retention = retention;  // Optional
	}
	
	public DDMTreeModify(DDQuotaConfig quotaConfig) {
		this.quotaConfig = quotaConfig;
	}

	public String toString() {
        return new Gson().toJson(this).toString();
    }

}
