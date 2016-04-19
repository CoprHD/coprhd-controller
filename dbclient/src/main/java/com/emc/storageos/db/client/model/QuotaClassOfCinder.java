/**
 *  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;



/**
 * QuotaClassOfCinder data object
 */
@Cf("QuotaClassOfCinder")
@DbKeyspace(Keyspaces.LOCAL)
public class QuotaClassOfCinder extends DataObject {
    /**
	 * 
	 */	   
	
    private String limits;
    private String quotaClass;
    
    @Name("quotaClass")
    public String getQuotaClass() {
		return quotaClass;
	}

	public void setQuotaClass(String quotaClass) {
		this.quotaClass = quotaClass;
		setChanged("quotaClass");
	}

	@Name("limits")
	public String getLimits() {
    	return (null == limits) ? "" : limits;		
	}

	public void setLimits(String limits) {
		this.limits = limits;
		setChanged("limits");
	}
	    
    public String toString(){
    	StringBuffer buf = new StringBuffer();
    	buf.append("Id:"+this.getId().toString()+"\n");    	
    	buf.append("limits:"+this.getLimits().toString()+"\n");
    	return buf.toString();
    }	
    
}
