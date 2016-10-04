/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * FilePolicyProfile will hold information about the how user been profiled their file policies.
 * 
 * @author lakhiv
 * 
 */

@Cf("FilePolicyProfile")
public class FilePolicyProfile extends DataObject {

    // Name of the profile
    private String profileName;
    
    private NamedURI virtualPool;
    
    // Tenant named URI
    private NamedURI tenantOrg;
    
    // Which level associated policies should apply!!
    //private String applyAt;
    
    // This is applicable only for project level policies.
    private Boolean applyToAllProjects;
    
    // List of projects associated to this profile
    // applicable, if the profile apply at project level.
    private StringSet associateProjects;
    
    // List of policies associated at vpool level
    private StringSet associatevPoolPolicies;
    
    // List of policies associated  at project level
    private StringSet associatePojectPolicies;
    
    // List of policies associated  at file system level
    private StringSet associateFsPolicies;
    
    @Name("profileName")
    public String getProfileName() {
		return profileName;
	}

	public void setProfileName(String profileName) {
		this.profileName = profileName;
		setChanged("profileName");
	}

	@NamedRelationIndex(cf = "NamedRelation", type = VirtualPool.class)
	@Name("virtualPool")
	public NamedURI getVirtualPool() {
		return virtualPool;
	}

	public void setVirtualPool(NamedURI virtualPool) {
		this.virtualPool = virtualPool;
		setChanged("virtualPool");
	}
	
	@NamedRelationIndex(cf = "NamedRelation", type = TenantOrg.class)
    @Name("tenantOrg")
    public NamedURI getTenantOrg() {
        return tenantOrg;
    }

    public void setTenantOrg(NamedURI tenantOrg) {
        this.tenantOrg = tenantOrg;
        setChanged("tenantOrg");
    }

	/*@Name("applyAt")
	public String getApplyAt() {
		return applyAt;
	}

	public void setApplyAt(String applyAt) {
		this.applyAt = applyAt;
		setChanged("applyAt");
	}*/

	@Name("associateProjects")
	public StringSet getAssociateProjects() {
		return associateProjects;
	}

	public void setAssociateProjects(StringSet associateProjects) {
		this.associateProjects = associateProjects;
		setChanged("associateProjects");
	}

	@Name("applyToAllProjects")
	public Boolean getApplyToAllProjects() {
		return applyToAllProjects;
	}

	public void setApplyToAllProjects(Boolean applyToAllProjects) {
		this.applyToAllProjects = applyToAllProjects;
		setChanged("applyToAllProjects");
	}

	@Name("associatevPoolPolicies")
	public StringSet getAssociatevPoolPolicies() {
		return associatevPoolPolicies;
	}

	public void setAssociatevPoolPolicies(StringSet associatevPoolPolicies) {
		this.associatevPoolPolicies = associatevPoolPolicies;
		setChanged("associatevPoolPolicies");
	}

	@Name("associatePojectPolicies")
	public StringSet getAssociatePojectPolicies() {
		return associatePojectPolicies;
	}

	public void setAssociatePojectPolicies(StringSet associatePojectPolicies) {
		this.associatePojectPolicies = associatePojectPolicies;
		setChanged("associatePojectPolicies");
	}

	@Name("associateFsPolicies")
	public StringSet getAssociateFsPolicies() {
		return associateFsPolicies;
	}

	public void setAssociateFsPolicies(StringSet associateFsPolicies) {
		this.associateFsPolicies = associateFsPolicies;
		setChanged("associateFsPolicies");
	}
	
}
