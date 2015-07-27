/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeLanBootImagePath")
public class ComputeLanBootImagePath extends DiscoveredSystemObject {
	
	private String dn;
	private String type;
	private String vnicName;
	private URI computeLanBoot;
	
	@Name("dn")
	public String getDn() {
		return dn;
	}
	public void setDn(String dn) {
		this.dn = dn;
		setChanged("dn");
	}
	
	@Name("type")
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
		setChanged("type");
	}
	
	@Name("vnicName")
	public String getVnicName() {
		return vnicName;
	}
	public void setVnicName(String vnicName) {
		this.vnicName = vnicName;
		setChanged("vnicName");
	}
	
	@RelationIndex(cf = "RelationIndex", type = ComputeLanBoot.class)
	@Name("computeLanBoot")
	public URI getComputeLanBoot() {
		return computeLanBoot;
	}
	public void setComputeLanBoot(URI computeLanBoot) {
		this.computeLanBoot = computeLanBoot;
		setChanged("computeLanBoot");
	}
	
	
	
}
