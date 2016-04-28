/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

public abstract class IsilonPool {
	
	public abstract String getNativeId();
	public abstract String getName();
	public abstract Long getAvailableBytes();
	public abstract Long getFreeBytes();
	public abstract Long getUsedBytes();
	public abstract Long getTotalBytes();
}
