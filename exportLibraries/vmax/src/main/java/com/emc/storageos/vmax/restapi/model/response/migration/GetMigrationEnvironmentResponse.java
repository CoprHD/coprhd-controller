package com.emc.storageos.vmax.restapi.model.response.migration;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class GetMigrationEnvironmentResponse {
    @SerializedName("arrayId")
    @JsonProperty(value = "arrayId")
    private List<String> arrayIdList;

    public List<String> getArrayIdList() {
        return arrayIdList;
    }

    public void setArrayIdList(List<String> arrayIdList) {
        this.arrayIdList = arrayIdList;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
