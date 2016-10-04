/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a policy profile, specified
 * during policy profile updation.
 * 
 * @author lakhiv
 * 
 */

@XmlRootElement(name = "file_policy_profile_update")
public class FilePolicyProfileUpdateParam {

    
    private String virtualPool;
    
    // Which level associated policies should apply!!
    private String applyAt;
    
    // This is applicable only for project level policies.
    private Boolean applyToAllProjects;
    
    // List of projects associated to this profile
    // applicable, if the profile apply at project level.
    private List<String> addProjects;
    
    private List<String> removeProjects;
    
    // List of policies associated to this profile
    private List<String> addPolicies;
    private List<String> removePolicies;


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

	@XmlElement(name = "add_projects")
	public List<String> getAddProjects() {
		return addProjects;
	}

	public void setAddProjects(List<String> addProjects) {
		this.addProjects = addProjects;
	}

	@XmlElement(name = "reove_projects")
	public List<String> getRemoveProjects() {
		return removeProjects;
	}

	public void setRemoveProjects(List<String> removeProjects) {
		this.removeProjects = removeProjects;
	}

	@XmlElement(name = "add_policies")
	public List<String> getAddPolicies() {
		return addPolicies;
	}

	public void setAddPolicies(List<String> addPolicies) {
		this.addPolicies = addPolicies;
	}

	@XmlElement(name = "remove_policies")
	public List<String> getRemovePolicies() {
		return removePolicies;
	}

	public void setRemovePolicies(List<String> removePolicies) {
		this.removePolicies = removePolicies;
	}
}
