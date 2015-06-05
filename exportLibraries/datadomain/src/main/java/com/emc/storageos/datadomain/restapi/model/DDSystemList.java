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
