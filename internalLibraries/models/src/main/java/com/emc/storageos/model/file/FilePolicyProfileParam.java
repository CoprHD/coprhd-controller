/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Attributes associated with a policy profile, specified
 * during policy profile creation.
 * 
 * @author lakhiv
 * 
 */

@XmlRootElement(name = "file_policy_profile_create")
public class FilePolicyProfileParam {

    // file policy profile name
    private String profileName;
    
    private String virtualPool;
    
    // Which level associated policies should apply!!
    private String applyAt;
    
    // This is applicable only for project level policies.
    private Boolean applyToAllProjects;
    
    // List of projects associated to this profile
    // applicable, if the profile apply at project level.
    private List<String> associatedProjects;
    
    // List of policies associated to this profile
    private List<String> associatePolicies;

    @XmlElement(required = true, name = "profile_name")
    @Length(min = 2, max = 128)
	public String getProfileName() {
		return profileName;
	}

	public void setProfileName(String profileName) {
		this.profileName = profileName;
	}

	@XmlElement(required = true, name = "vpool")
	public String getVirtualPool() {
		return virtualPool;
	}

	public void setVirtualPool(String virtualPool) {
		this.virtualPool = virtualPool;
	}

	@XmlElement(required = true, name = "apply_at")
	public String getApplyAt() {
		return applyAt;
	}

	public void setApplyAt(String applyAt) {
		this.applyAt = applyAt;
	}
	
	@XmlElement(name = "apply_to_all_projects")
	public Boolean getApplyToAllProjects() {
		return applyToAllProjects;
	}

	public void setApplyToAllProjects(Boolean applyToAllProjects) {
		this.applyToAllProjects = applyToAllProjects;
	}

	@XmlElement(name = "associated_projects")
	public List<String> getAssociatedProjects() {
		return associatedProjects;
	}

	public void setAssociatedProjects(List<String> associatedProjects) {
		this.associatedProjects = associatedProjects;
	}

	@XmlElement(name = "associate_policies")
	public List<String> getAssociatePolicies() {
		return associatePolicies;
	}

	public void setAssociatePolicies(List<String> associatePolicies) {
		this.associatePolicies = associatePolicies;
	}
}
