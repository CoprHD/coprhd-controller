package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class XtremIOObjectInfo {
	
	@SerializedName("name")
	@JsonProperty(value="name")
	private String name;

	@SerializedName("href")
	@JsonProperty(value="href")
	private String href;
	
	public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String toString() {
		return new Gson().toJson(this).toString();
	}

}
