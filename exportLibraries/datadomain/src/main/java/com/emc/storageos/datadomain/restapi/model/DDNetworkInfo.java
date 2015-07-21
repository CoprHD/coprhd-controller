/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value="network")
public class DDNetworkInfo {

    private String id;

    private String name;

//    private boolean enabled;
    
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

//    public boolean getEnabled() {
//        return enabled;
//    }
//    public void setName(boolean enabled) {
//        this.enabled = enabled;
//    }
    
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
