package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.db.client.model.StoragePool;

public class ObjectDeviceInputOutput {
	private String name;
	private String namespace;
	private String repGroup;
	private String retentionPeriod;
	private String blkSizeHQ;
	private String notSizeSQ;
	private String owner;
	
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}
	
	public String getNamespace() {
		return namespace;
	}

	public void setRepGroup(String repGroup) {
		this.repGroup = repGroup;
	}
	
	public String getRepGroup() {
		return repGroup;
	}

	public void setRetentionPeriod(String retentionPeriod) {
		this.retentionPeriod = retentionPeriod;
	}
	
	public String getRetentionPeriod() {
		return retentionPeriod;
	}

	public void setBlkSizeHQ(String blkSizeHQ) {
		this.blkSizeHQ = blkSizeHQ;
	}
	
	public String getBlkSizeHQ() {
		return blkSizeHQ;
	}

	public void setNotSizeSQ(String notSizeSQ) {
		this.notSizeSQ = notSizeSQ;
	}
	
	public String getNotSizeSQ() {
		return notSizeSQ;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}
	
	public String getOwner() {
		return owner;
	}

}
