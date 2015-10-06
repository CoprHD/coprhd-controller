/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder.model;

public class ConsistencyGroupCreateRequest {
	
	/**
	 * Json representation for creating consistency Grp
	 * 
	 * {"consistencygroup" : { 
	 *          "status":"creating", 
	 *          "user_id":null, 
	 *          "name":"cg1", 
	 *          "availability_zone":null, 
	 *          "volume_types":null, 
	 *          "project_id":null, 
	 *          "description":null }
	 *          }
	 * 
	 */
	
	public Consistencygroup consistencygroup = new Consistencygroup();
	
	public class Consistencygroup {
		
		public String status;
		public String user_id;
		public String name;
		public String availability_zone;
		public String volume_types;
		public String project_id;
		public String description;
	}
	

}


