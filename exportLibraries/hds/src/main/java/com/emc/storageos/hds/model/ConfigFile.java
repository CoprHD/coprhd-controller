/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.hds.model;

public class ConfigFile {
	private String objectID;
	
	private String instanceNumber;
	
	private String portNumber;
	
	private String controlledBy;
	
	private String valid;
	
	private ReplicationGroup replicationGroup;
	
	public String getObjectID() {
		return objectID;
	}
	public void setObjectID(String objectID) {
		this.objectID = objectID;
	}
	public String getInstanceNumber() {
		return instanceNumber;
	}
	public void setInstanceNumber(String instanceNumber) {
		this.instanceNumber = instanceNumber;
	}
	public String getPortNumber() {
		return portNumber;
	}
	public void setPortNumber(String portNumber) {
		this.portNumber = portNumber;
	}
	public String getControlledBy() {
		return controlledBy;
	}
	public void setControlledBy(String controlledBy) {
		this.controlledBy = controlledBy;
	}
	public String getValid() {
		return valid;
	}
	public void setValid(String valid) {
		this.valid = valid;
	}
	public ReplicationGroup getReplicationGroup() {
		return replicationGroup;
	}
	public void setReplicationGroup(ReplicationGroup replicationGroup) {
		this.replicationGroup = replicationGroup;
	}
}
