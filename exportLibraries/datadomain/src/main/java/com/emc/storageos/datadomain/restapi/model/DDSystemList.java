/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;
import java.util.List;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value="dd_systems")
public class DDSystemList {

    @SerializedName("system_info")
    @JsonProperty(value="system_info")
    private List<DDSystemInfo> systemInfo;

	public List<DDSystemInfo> getSystemInfo() {
		return systemInfo;
	}

	public void setSystemInfo(List<DDSystemInfo> systemInfo) {
		this.systemInfo = systemInfo;
	}

}
