/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class ConsistencyGroupResult {
	private Integer id;
	private String name;
	private ArrayList<String> setmembers;
	private boolean qosEnabled;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ArrayList<String> getSetmembers() {
		return setmembers;
	}

	public void setSetmembers(ArrayList<String> setmembers) {
		this.setmembers = setmembers;
	}

	public boolean isQosEnabled() {
		return qosEnabled;
	}

	public void setQosEnabled(boolean qosEnabled) {
		this.qosEnabled = qosEnabled;
	}

	public String getDetails() {
		return " ConsistencyGroupResult id = "+id + " memebers= " + setmembers + "qosEnabled =" + qosEnabled;
	}
}
