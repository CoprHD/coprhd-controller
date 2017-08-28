/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_ig")
public class XtremIOInitiatorGroup {
    @SerializedName("num-of-initiators")
    @JsonProperty(value = "num-of-initiators")
    private String numberOfInitiators;

    @SerializedName("num-of-vols")
    @JsonProperty(value = "num-of-vols")
    private String numberOfVolumes;

    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    @SerializedName("index")
    @JsonProperty(value = "index")
    private String index;

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumberOfInitiators() {
        return numberOfInitiators;
    }

    public void setNumberOfInitiators(String numberOfInitiators) {
        this.numberOfInitiators = numberOfInitiators;
    }

    public String getNumberOfVolumes() {
        return numberOfVolumes;
    }

    public void setNumberOfVolumes(String numberOfVolumes) {
        this.numberOfVolumes = numberOfVolumes;
    }

	@Override
	public String toString() {
		return "XtremIOInitiatorGroup [numberOfInitiators=" + numberOfInitiators + ", numberOfVolumes="
				+ numberOfVolumes + ", name=" + name + ", index=" + index + "]";
	}

}
