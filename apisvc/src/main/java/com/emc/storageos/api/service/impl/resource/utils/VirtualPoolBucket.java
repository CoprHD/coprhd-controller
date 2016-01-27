package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.model.VirtualPool;

public class VirtualPoolBucket {
	
	public VirtualPoolBucket(String vpoolType, VirtualPool vpool) {
		this.vpoolType = vpoolType;
		this.vPool = vpool;
	}
	
	    	public String getVpoolType() {
		return vpoolType;
	}
	public void setVpoolType(String vpoolType) {
		this.vpoolType = vpoolType;
	}
	public VirtualPool getvPool() {
		return vPool;
	}
	public void setvPool(VirtualPool vPool) {
		this.vPool = vPool;
	}
			private String vpoolType;
	        private VirtualPool vPool;
	    

}
