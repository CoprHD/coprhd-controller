/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class CifsShareDeleteParam {
	private VNXeBase cifsShare;

	public VNXeBase getCifsShare() {
		return cifsShare;
	}

	public void setCifsShare(VNXeBase cifsShare) {
		this.cifsShare = cifsShare;
	}
	

}
