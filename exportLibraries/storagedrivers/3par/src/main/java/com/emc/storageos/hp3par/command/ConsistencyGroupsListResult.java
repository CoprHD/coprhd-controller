/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class ConsistencyGroupsListResult {
	private Integer total;
	private ArrayList<ConsistencyGroupResult> members;
	public Integer getTotal() {
		return total;
	}
	public void setTotal(Integer total) {
		this.total = total;
	}
	public ArrayList<ConsistencyGroupResult> getMembers() {
		return members;
	}
	public void setMembers(ArrayList<ConsistencyGroupResult> members) {
		this.members = members;
	}
	
}
