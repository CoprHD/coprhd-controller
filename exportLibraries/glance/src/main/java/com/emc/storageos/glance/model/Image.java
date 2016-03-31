package com.emc.storageos.glance.model;

//for Glance image version V1
public class Image {

		private String uri;
		private String name;
		private String diskFormat;
		private String containerFormat;
		private String size;
		private String checksum;
		private String createdAt;
		private String updatedAt;
		private String deletedAt;
		private String status;
		private Boolean isPublic;
		private Integer minRam;
		private Integer minDisk;
		private Object owner;
		private Properties properties;

}
