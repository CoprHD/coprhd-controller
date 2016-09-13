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
    private String applyAt;
    
    // List of projects associated to this profile
    // applicable, if the profile apply at project level.
    private StringSet associateProjects;
    
    // List of policies associated to this profile
    private StringSet associatePolicies;
    
    @Name("profileName")
    public String getProfileName() {
		return profileName;
	}

	public void setProfileName(String profileName) {
		this.profileName = profileName;
	}

	@NamedRelationIndex(cf = "NamedRelation", type = VirtualPool.class)
	@Name("virtualPool")
	public NamedURI getVirtualPool() {
		return virtualPool;
	}

	public void setVirtualPool(NamedURI virtualPool) {
		this.virtualPool = virtualPool;
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

	@Name("applyAt")
	public String getApplyAt() {
		return applyAt;
	}

	public void setApplyAt(String applyAt) {
		this.applyAt = applyAt;
	}

	@Name("associatedProjects")
	public StringSet getAssociateProjects() {
		return associateProjects;
	}

	public void setAssociateProjects(StringSet associateProjects) {
		this.associateProjects = associateProjects;
	}

	@Name("associatedPolicies")
	public StringSet getAssociatePolicies() {
		return associatePolicies;
	}

	public void setAssociatePolicies(StringSet associatePolicies) {
		this.associatePolicies = associatePolicies;
	}
	
}
