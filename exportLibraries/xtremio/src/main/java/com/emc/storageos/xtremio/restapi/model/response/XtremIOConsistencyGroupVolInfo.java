/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.xtremio.restapi.model.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_consistency_groups_info")
public class XtremIOConsistencyGroupVolInfo {
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOConsistencyGroup content;
    
    @SerializedName("vol-list")
    @JsonProperty(value = "vol-list")
    private List<List<List<Object>>> volList; 
    
    @SerializedName("num-of-vols")
    @JsonProperty(value = "num-of-vols")
    private String numOfVols; 

    public String getNumOfVols() {
		return numOfVols;
	}

	public void setNumOfVols(String numOfVols) {
		this.numOfVols = numOfVols;
	}

	public List<List<List<Object>>> getVolList() {
		return volList;
	}

	public void setVolList(List<List<List<Object>>> volList) {
		this.volList = volList;
	}

	public XtremIOConsistencyGroup getContent() {
        return content;
    }

    public void setContent(XtremIOConsistencyGroup content) {
        this.content = content;
        
    }

	@Override
	public String toString() {
		return "XtremIOConsistencyGroupVolInfo [content=" + content
				+ ", volList=" + volList + "]";
	}


}
