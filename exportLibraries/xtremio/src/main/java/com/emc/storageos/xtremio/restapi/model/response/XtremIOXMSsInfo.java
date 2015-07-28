package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class XtremIOXMSsInfo {
	
	@SerializedName("xmss")
	@JsonProperty(value="xmss")
	private XtremIOObjectInfo[] xmssInfo;

	public XtremIOObjectInfo[] getXmssInfo() {
		return xmssInfo;
	}

	public void setXmssInfo(XtremIOObjectInfo[] xmssInfo) {
		this.xmssInfo = xmssInfo;
	}
	
	public String toString() {
		return new Gson().toJson(this).toString();
	}

}
