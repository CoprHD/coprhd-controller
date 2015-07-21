/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.model;

public class VolumeCreateResponse {
	
	/**
	 * The volume create request response json model
	 * {
   		"volume":{
      		"status":"creating",
      		"display_name":"my_volume",
      		"attachments":[
 
      						],
      		"availability_zone":"nova",
      		"bootable":"false",
      		"created_at":"2014-02-21T19:52:04.949734",
      		"display_description":null,
      		"volume_type":"None",
      		"snapshot_id":null,
      		"source_volid":null,
      		"metadata":{
 
      			},
      		"id":"93c2e2aa-7744-4fd6-a31a-80c4726b08d7",
      		"size":10
   			}
		}
	 */
	
	public Volume volume;
	public class Volume {
		public String status;
		public String display_name;
		public Attachement[] attachements;
		public String availability_zone;
		public boolean bootable;
		public String created_at;
		public String display_description;
		public String volume_type;
		public String snapshot_id;
		public String source_volid;
		public Metadata metadata;
		public String id;
		public long size;
	}
	
	public class Attachement {
		
	}
	
	public class Metadata {
		
	}

}
