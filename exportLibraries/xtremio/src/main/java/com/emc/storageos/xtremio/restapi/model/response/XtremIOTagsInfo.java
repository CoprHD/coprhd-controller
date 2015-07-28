package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class XtremIOTagsInfo {
	
	@SerializedName("tags")
	@JsonProperty(value="tags")
	private XtremIOObjectInfo[] tagsInfo;

	public XtremIOObjectInfo[] getTagsInfo() {
		return tagsInfo;
	}

	public void setTagsInfo(XtremIOObjectInfo[] tagsInfo) {
		this.tagsInfo = tagsInfo;
	}
	
	public String toString() {
		return new Gson().toJson(this).toString();
	}
}
