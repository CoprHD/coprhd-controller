/* Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

public class UsageAndLimits {
    private long reserved = 0;
    private long limit;
    private long in_use;
    
    public long getReserved() {
		return reserved;
	}
	public void setReserved(long reserved) {
		this.reserved = reserved;
	}
	public long getLimit() {
		return limit;
	}
	public void setLimit(long limit) {
		this.limit = limit;
	}
	public long getIn_use() {
		return in_use;
	}
	public void setIn_use(long in_use) {
		this.in_use = in_use;
	}
	
}
