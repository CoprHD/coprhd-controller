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
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;

import com.google.gson.annotations.SerializedName;

public class SnapshotActionRequest {
	
	/**
	 * Json model for snapshot update status request
	 *{"os-reset_status": 
		{"status": "available"}
	  }
	 */
	@XmlElement(name="os-reset_status")
	@SerializedName("os-reset_status")
	public UpdateStatus updateStatus = new UpdateStatus();
	
	public class UpdateStatus {
		public String status;		
	}
	
}
