/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeBootDef")
public class ComputeBootDef extends DiscoveredSystemObject {

	private URI computeSystem;
	private URI serviceProfileTemplate;
	private Boolean enforceVnicVhbaNames ;
	
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
	@RelationIndex(cf = "RelationIndex", type = UCSServiceProfileTemplate.class)
	@Name("serviceProfileTemplate")
	public URI getServiceProfileTemplate(){
		return serviceProfileTemplate;
	}
	public void setServiceProfileTemplate(URI serviceProfileTemplate){
		this.serviceProfileTemplate = serviceProfileTemplate;
		setChanged("serviceProfileTemplate");
	}

	
	
}
