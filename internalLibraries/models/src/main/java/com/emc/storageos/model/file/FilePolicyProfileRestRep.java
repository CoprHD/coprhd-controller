/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Attributes associated with a policy profile
 * 
 * @author lakhiv
 * 
 */

@XmlRootElement(name = "file_policy_profile")
public class FilePolicyProfileRestRep extends NamedRelatedResourceRep {

    // file policy profile name
    private String profileName;
    
    private String virtualPool;
    
    // Which level associated policies should apply!!
    private String applyAt;
    
    // List of projects associated to this profile
    // applicable, if the profile apply at project level.
    private List<String> associatedProjects;
    
    // List of policies associated to this profile
    private List<String> associatePolicies;

    @XmlElement(name = "profile_name")
	public String getProfileName() {
		return profileName;
	}

	public void setProfileName(String profileName) {
		this.profileName = profileName;
	}

	@XmlElement(name = "vpool")
	public String getVirtualPool() {
		return virtualPool;
	}

	public void setVirtualPool(String virtualPool) {
		this.virtualPool = virtualPool;
	}

	@XmlElement(name = "apply_at")
	public String getApplyAt() {
		return applyAt;
	}

	public void setApplyAt(String applyAt) {
		this.applyAt = applyAt;
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
