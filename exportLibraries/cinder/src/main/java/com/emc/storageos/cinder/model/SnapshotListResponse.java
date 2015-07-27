/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.model;

public class SnapshotListResponse {

	/*{
		   "snapshots":[
		      {
		         "status":"available",
		         "description":"Very important",
		         "created_at":"2013-02-25T04:13:17.000000",
		         "metadata":{
		 
		         },
		         "volume_id":"5aa119a8-d25b-45a7-8d1b-88e127885635",
		         "size":1,
		         "id":"2bb856e1-b3d8-4432-a858-09e4ce939389",
		         "name":"snap-001"
		      },
		      {
		         "status":"available",
		         "description":"Weekly backup",
		         "created_at":"2013-02-25T07:20:38.000000",
		         "metadata":{
		 
		         },
		         "volume_id":"806092e3-7551-4fff-a005-49016f4943b1",
		         "size":1,
		         "id":"e820db06-58b5-439d-bac6-c01faa3f6499",
		         "name":"snap-002"
		      }
		   ]
		}*/
	
	public Snapshot snapshots[];
	
	public class Snapshot 
	{
		public String status;
		public String description;
		public String created_at;
		public Metadata metadata;
		public String volume_id;
		public int size;
		public String id;
		public String name;
	}
	
	public class Metadata
	{
		
	}

}
