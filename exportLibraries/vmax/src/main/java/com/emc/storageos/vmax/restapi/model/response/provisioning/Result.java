package com.emc.storageos.vmax.restapi.model.response.provisioning;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

// Refer StorageGroupVolumeListResponse
public class Result {

    @SerializedName("volumeId")
    @JsonProperty(value = "volumeId")
    private List<String> volumeId;

    @SerializedName("from")
    @JsonProperty(value = "from")
    private int from;

    @SerializedName("to")
    @JsonProperty(value = "to")
    private int to;

    public List<String> getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(List<String> volumeId) {
        this.volumeId = volumeId;
    }

    public int getFrom() {
        return from;
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public int getTo() {
        return to;
    }

    public void setTo(int to) {
        this.to = to;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
