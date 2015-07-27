/*
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

import org.codehaus.jackson.map.annotate.JsonRootName;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

/**
 * Represents list of Cifs shares
 */
@JsonRootName(value="shares")
public class DDShareList {

    private List<DDShareInfo> share;

	public List<DDShareInfo> getShares() {
		return share;
	}

	public void setShares(List<DDShareInfo> shares) {
		this.share = shares;
	}

}
