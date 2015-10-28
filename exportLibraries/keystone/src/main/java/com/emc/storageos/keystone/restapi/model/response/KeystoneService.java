/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.keystone.restapi.model.response;

public class KeystoneService 
{
	private String id;
	private boolean enabled;
	private String name;
	private String type;
	private String description;

	
	public String getId() {
		return id;
		}
	
	public void setId(String id) {
		this.id = id;
		}
	
	public boolean getEnabled() {
		return enabled;
		}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		}
	
	public String getName() {
		return name;
		}
	
	public void setName(String name) {
		this.name = name;
		}
	
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public String getDescription() {
		return description;
		}
	
	public void setDescription(String description) {
		this.description = description;
		}

}
