/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.network;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * This represents an wwn alias include: 
 *      name - name of alias
 *      address - WWN format
 */
@XmlRootElement(name="wwn_alias")
public class WwnAliasParam implements Serializable {

    private String name;
    private String address;
    /**
     * marked transient because it cannot be serialized 
     */
    // The path to the ZoneAlias object in SIM DB
    transient Object cimObjectPath = null;
    // The path to the ZoneMembershipSettingData object for 
    // the alias WWN member
    transient Object cimMemberPath = null;
    
    public WwnAliasParam() {};
    
    public WwnAliasParam(String name) {
        setName(name);
    }
        
    public WwnAliasParam(String name, String address) {
        this(name);
        setAddress(address);
    }
    
    /**
     * The alias WWN
     * @return The alias WWN
     */
    @XmlElement
	public String getAddress() {
		return address;
	}
	
	public void setAddress(String address) {
	    this.address = address;
	}
	
	/**
	 * The alias name
	 * @return The alias name
	 */
	@XmlElement (required=true)
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    @XmlTransient
    public Object getCimObjectPath() {
        return cimObjectPath;
    }
    public void setCimObjectPath(Object cimObjectPath) {
        this.cimObjectPath = cimObjectPath;
    }

    @XmlTransient
    public Object getCimMemberPath() {
        return cimMemberPath;
    }
    public void setCimMemberPath(Object cimMemberPath) {
        this.cimMemberPath = cimMemberPath;
    }
    
}
