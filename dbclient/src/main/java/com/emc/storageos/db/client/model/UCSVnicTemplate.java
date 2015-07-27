/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("UCSVnicTemplate")
public class UCSVnicTemplate extends DiscoveredSystemObject {
	
	private String name;
	private URI computeSystem;
	private String dn;
	private String templateType;

	/**
     * Updating or non-updating
     */
    private Boolean updating;



	@Name("name")
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
		setChanged("name");
	}
	
	@RelationIndex(cf = "RelationIndex", type = ComputeSystem.class)
	@Name("computeSystem")
	public URI getComputeSystem() {
		return computeSystem;
	}
	public void setComputeSystem(URI computeSystem) {
		this.computeSystem = computeSystem;
		setChanged("computeSystem");
	}
	
	    @Name("dn")
    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
        setChanged("dn");
    }

    @Name("templateType")
    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
        setChanged("templateType");
    }

	@Name("updating")
    public Boolean getUpdating() {
        return updating;
    }

    public void setUpdating(Boolean updating) {
        this.updating = updating;
        setChanged("updating");
    }
	
}
