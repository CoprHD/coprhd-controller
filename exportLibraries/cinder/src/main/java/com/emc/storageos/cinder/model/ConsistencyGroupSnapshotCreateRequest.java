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


public class ConsistencyGroupSnapshotCreateRequest {

	/**
	 * Json representation for creating consistency Snapshot
	 * 
	 * {"cgsnapshot" : { 
	 *          "status":"creating",  
	 *          "name":"cgsnap1",  
	 *          "project_id":null, 
	 *          "description":null,
	 *          "consistencygroup_id":"cg1" }
	 *          }
	 * 
	 */
	
	public Cgsnapshot cgsnapshot = new Cgsnapshot();
	
	public class Cgsnapshot {
		
		public String status;
		public String user_id;
		public String name;
		public String consistencygroup_id;
		public String project_id;
		public String description;
	}
	
}
