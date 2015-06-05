/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
package com.emc.storageos.recoverpoint.objectmodel;

import java.util.Set;


/**
 * A site's key and the arrays that are visible to it.
 * 
 */
public class SiteArrays {
	private RPSite _site;
	private Set<String> _arrays;
	
	public RPSite getSite() {
		return _site;
	}
	public void setSite(RPSite site) {
		this._site = site;
	}
	public Set<String> getArrays() {
		return _arrays;
	}
	public void setArrays(Set<String> arrays) {
		this._arrays = arrays;
	}
}
