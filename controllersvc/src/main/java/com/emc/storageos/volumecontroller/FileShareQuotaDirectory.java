/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;

import com.emc.storageos.db.client.model.QuotaDirectory;


/**
 * Place holder for FS QuotaDirectory information.
 */
@SuppressWarnings("serial")
public class FileShareQuotaDirectory implements Serializable {
	
	// enumeration of qtree security styles
	public enum SecurityStyles {
		parents,
		unix,
	    ntfs,
	    mixed
	}

	private URI _id;
	private String _name;
	private String _securityStyle;
    private Boolean _oplock;
    private Long _size; 
	
    // private SecurityStyles _securityStyle;
    
    public String getSecurityStyle() {
        return _securityStyle.toString();
    }
    
    /**
     * Construction of FileShareQtree
     * @param name
     */
    public FileShareQuotaDirectory (QuotaDirectory qtree) {
    	_id = qtree.getId();
    	_name = qtree.getName();
    	_securityStyle = qtree.getSecurityStyle();
    	_oplock = qtree.getOpLock();
    	_size = qtree.getSize();
    }
    
    public String getName() {
        return _name;
    }
    
    public URI getId() {
        return _id;
    }

    public void setId(URI id) {
        _id = id;
    }
    
    public Boolean getOpLock() {
    	return _oplock;
    }
    
    public Long getSize() {
    	return _size;
    }
    
}
