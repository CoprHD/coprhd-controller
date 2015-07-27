/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_clusters")
public class XtremIOClusters {
	
	@SerializedName("clusters")
	@JsonProperty(value="clusters")
	private XtremIOCluster[] clusters;
	
	public String toString() {
		return new Gson().toJson(this).toString();
	}

    public XtremIOCluster[] getClusters() {
        return clusters != null ? clusters.clone() : clusters;
    }

    public void setClusters(XtremIOCluster[] clusters) {
    	if(clusters != null){
    		this.clusters = clusters.clone();
    	}
    }
}

