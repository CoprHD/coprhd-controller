/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author jainm15
 */
@XmlRootElement(name = "filepolicy_filesystem_assign_param")
public class FilePolicyFileSystemAssignParam implements Serializable {

	private static final long serialVersionUID = 1L;

	private Set<URI> targetVArrays;

	public FilePolicyFileSystemAssignParam() {
		super();
	}

	@XmlElementWrapper(name = "target_varrays")
	@XmlElement(name = "target_varray")
	public Set<URI> getTargetVArrays() {
		return targetVArrays;
	}

	public void setTargetVArrays(Set<URI> targetVArrays) {
		this.targetVArrays = targetVArrays;
	}
}
