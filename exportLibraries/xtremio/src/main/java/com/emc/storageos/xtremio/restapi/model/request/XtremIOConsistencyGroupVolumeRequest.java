package com.emc.storageos.xtremio.restapi.model.request;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_cg_volume_request")
public class XtremIOConsistencyGroupVolumeRequest {
	
	@SerializedName("cg-id")
    @JsonProperty(value="cg-id")
	private String cgName;
	
	@SerializedName("vol-id")
    @JsonProperty(value="vol-id")
	private String volName;
	
	@SerializedName("cluster-id")
    @JsonProperty(value="cluster-id")
	private String clusterName;

	public String getCgName() {
		return cgName;
	}

	public void setCgName(String cgName) {
		this.cgName = cgName;
	}

	public String getVolName() {
		return volName;
	}

	public void setVolName(String volName) {
		this.volName = volName;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	@Override
	public String toString() {
		return "XtremIOConsistencyGroupVolume [cgName=" + cgName + ", volName="
				+ volName + ", clusterName=" + clusterName + "]";
	}
}
