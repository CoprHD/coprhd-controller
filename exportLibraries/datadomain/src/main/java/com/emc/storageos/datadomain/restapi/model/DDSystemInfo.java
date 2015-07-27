/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;

/**
 * Created by zeldib on 2/10/14.
 */
public class DDSystemInfo {

    private String id;

    private String name;
    
    private DDRestLinkRep link;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public DDRestLinkRep getLink() {
		return link;
	}

	public void setLink(DDRestLinkRep link) {
		this.link = link;
	}
	
	public String toString() {
    	return new Gson().toJson(this).toString();
    }

}
