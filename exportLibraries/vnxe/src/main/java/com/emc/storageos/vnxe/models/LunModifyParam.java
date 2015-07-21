/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

/**
 * Parameters for modifying lun
 *
 */

public class LunModifyParam extends LunCreateParam{
	private VNXeBase lun;
	
	public VNXeBase getLun() {
		return lun;
	}
	public void setLun(VNXeBase lun) {
		this.lun = lun;
	}	

}
