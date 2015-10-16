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
