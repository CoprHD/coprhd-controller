/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class NfsShareModifyParam {
	private VNXeBase nfsShare;
	private NfsShareParam  nfsShareParameters;
	public VNXeBase getNfsShare() {
		return nfsShare;
	}
	public void setNfsShare(VNXeBase nfsShare) {
		this.nfsShare = nfsShare;
	}
	public NfsShareParam getNfsShareParameters() {
		return nfsShareParameters;
	}
	public void setNfsShareParameters(NfsShareParam nfsShareParam) {
		this.nfsShareParameters = nfsShareParam;
	}
	

}
