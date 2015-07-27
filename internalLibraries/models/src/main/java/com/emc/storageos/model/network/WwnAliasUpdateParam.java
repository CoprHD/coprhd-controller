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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This represents an wwn alias to change "address" to "new address": 
 *      name - name of alias  (required)
 *      new_name - change alias name to this new name
 *      address - WWN format
 *      newAddress - change alias's address to this new address (required)
 */
@XmlRootElement(name="wwn_alias")
public class WwnAliasUpdateParam extends WwnAliasParam{

    private String newName;
    private String newAddress;
    
    public WwnAliasUpdateParam() {};
    
    @XmlElement(name="new_name")
    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    @XmlElement(name="new_address")
	public String getNewAddress() {
		return newAddress;
	}
	
	public void setNewAddress(String address) {
	    this.newAddress = address;
	}	
}
