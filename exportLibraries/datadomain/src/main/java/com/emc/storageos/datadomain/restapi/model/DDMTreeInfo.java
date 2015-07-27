/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value="mtree")
public class DDMTreeInfo {

    private String id;

    private String name;

    // delete status: 0: not deleted; 1: deleted 
//    @SerializedName("del_status")
//    @JsonProperty(value="del_status")
//    private Integer delStatus;
    
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

//	public Integer getDelStatus() {
//		return delStatus;
//	}
//
//	public void setDelStatus(Integer delStatus) {
//		this.delStatus = delStatus;
//	}

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
