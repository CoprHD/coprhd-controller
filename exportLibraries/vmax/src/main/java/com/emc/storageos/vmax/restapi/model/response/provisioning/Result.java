package com.emc.storageos.vmax.restapi.model.response.provisioning;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

// Refer ResultList
public class Result {

    @SerializedName("volumeId")
    @JsonProperty(value = "volumeId")
    private String volumeId;

    public String getVolumeId()
    {
        return volumeId;
    }

    public void setVolumeId(String volumeId)
    {
        this.volumeId = volumeId;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
