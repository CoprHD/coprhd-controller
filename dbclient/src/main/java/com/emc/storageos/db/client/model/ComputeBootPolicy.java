/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
/*
 * Represents named global boot policies on the UCS.
 */
@Cf("ComputeBootPolicy")
public class ComputeBootPolicy extends DiscoveredDataObject {

	private URI computeSystem;
	private String name;
	private Boolean enforceVnicVhbaNames ;
	private String dn;
	
	@RelationIndex(cf = "RelationIndex", type = ComputeSystem.class)
	@Name("computeSystem")
	public URI getComputeSystem() {
		return computeSystem;
	}
	public void setComputeSystem(URI computeSystem) {
		this.computeSystem = computeSystem;
		setChanged("computeSystem");
	}
	
	@Name("enforceVnicVhbaNames")
	public Boolean getEnforceVnicVhbaNames() {
		return enforceVnicVhbaNames;
	}
	public void setEnforceVnicVhbaNames(Boolean enforceVnicVhbaNames) {
		this.enforceVnicVhbaNames = enforceVnicVhbaNames;
		setChanged("enforceVnicVhbaNames");
	}


	@Name("name")
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
		setChanged("name");
	}
	
	@Name("dn")
	public String getDn() {
		return dn;
	}
	public void setDn(String dn) {
		this.dn = dn;
		setChanged("dn");
	}
}
