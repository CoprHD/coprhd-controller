/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
@Cf("ComputeSanBootImagePath")
public class ComputeSanBootImagePath extends DiscoveredSystemObject {
	
	private String portWWN;
	private String type;
	private Long hlu;
	private String vnicName;
	private String dn;
	private URI computeSanBootImage;
	
	@Name("portWWN")
	public String getPortWWN() {
		return portWWN;
	}
	public void setPortWWN(String portWWN) {
		this.portWWN = portWWN;
		setChanged("portWWN");
	}
	
	@Name("type")
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
		setChanged("type");
	}
	
	@Name("hlu")
	public Long getHlu() {
		return hlu;
	}
	public void setHlu(Long hlu) {
		this.hlu = hlu;
		setChanged("hlu");
	}
	
	@Name("vnicName")
	public String getVnicName() {
		return vnicName;
	}
	public void setVnicName(String vnicName) {
		this.vnicName = vnicName;
		setChanged("vnicName");
	}
	
	@Name("dn")
	public String getDn() {
		return dn;
	}
	public void setDn(String dn) {
		this.dn = dn;
		setChanged("dn");
	}
	
	@RelationIndex(cf = "RelationIndex", type = ComputeSanBootImage.class)
	@Name("computeSanBootImage")
	public URI getComputeSanBootImage() {
		return computeSanBootImage;
	}
	public void setComputeSanBootImage(URI computeSanBootImage) {
		this.computeSanBootImage = computeSanBootImage;
		setChanged("computeSanBootImage");
	}
	
}
