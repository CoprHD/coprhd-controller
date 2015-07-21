/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller;

public enum ImageServerConstants {

	PXELINUX_CFG_DIR("pxelinux.cfg/"),
	DEFAULT_FILE("default"),
	PXELINUX_0_FILE("pxelinux.0"),
	HTTP_DIR("http/"),
	SERVER_PY_FILE("server.py"),
	WGET_FILE("wget"),
	HTTP_KICKSTART_DIR("http/ks/"),
	HTTP_FIRSTBOOT_DIR("http/fb/"),
	HTTP_SUCCESS_DIR("http/success/"),
	HTTP_FAILURE_DIR("http/failure/");
	
	private final String value;
	
	private ImageServerConstants(String value) {
		this.value = value;
	}

	public String toString() {
		return value;
	}
	
}
