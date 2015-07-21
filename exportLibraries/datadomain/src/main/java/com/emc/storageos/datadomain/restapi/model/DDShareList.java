/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
