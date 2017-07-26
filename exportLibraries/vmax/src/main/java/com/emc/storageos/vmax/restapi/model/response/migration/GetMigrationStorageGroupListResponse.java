/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.migration;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "dmStorageGroupList")
public class GetMigrationStorageGroupListResponse {

    @SerializedName("name")
    @JsonProperty(value = "name")
    private List<String> nameList;

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

    public List<String> getNameList() {
        return nameList;
    }

    public void setNameList(List<String> nameList) {
        this.nameList = nameList;
    }
}
