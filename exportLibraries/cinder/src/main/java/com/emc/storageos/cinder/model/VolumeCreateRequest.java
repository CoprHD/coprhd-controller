/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.model;

public class VolumeCreateRequest {
	
	/**
	 * Json model representation for volume
	 * create request
	 * 
	 * {"volume":{
      		"availability_zone":null,
      		"source_volid":null,
      		"display_description":null,
      		"snapshot_id":null,
      		"size":10,
      		"display_name":"my_volume",
      		"imageRef":null,
      		"volume_type":null,
      		"metadata":{
 
      			}
   			}
		}
	 */
	public Volume volume = new Volume();	
	public class Volume
	{
		public String availability_zone;
		public String source_volid;
		public String display_description;
		public String snapshot_id;
		public long size;
		public String display_name;
		public String imageRef;
		public String volume_type;
		public Metadata metadata = new Metadata();
	}
	
	public class Metadata {
		
	}

}
