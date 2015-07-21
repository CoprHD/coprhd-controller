/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.model;

public class SnapshotCreateResponse {
	
	/**
	 * Json model for Snapshot create response
	 * 
	 * {
   		"snapshot":{
      		"status":"creating",
      		"description":"Daily backup",
      		"created_at":"2013-02-25T03:56:53.081642",
      		"metadata":{
 
      			},
      		"volume_id":"5aa119a8-d25b-45a7-8d1b-88e127885635",
      		"size":1,
      		"id":"ffa9bc5e-1172-4021-acaf-cdcd78a9584d",
      		"name":"snap-001"
   			}
		}
	 */
	
	public Snapshot snapshot;
	public class Snapshot {
		public String status;
		public String description;
		public String created_at;
		public Metadata metadata;
		public String volume_id;
		public long size;
		public String id;
		public String name;
	}
	
	public class Metadata {
		
	}
	
	
	

}
