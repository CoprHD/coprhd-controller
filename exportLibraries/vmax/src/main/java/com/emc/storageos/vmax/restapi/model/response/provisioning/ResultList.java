package com.emc.storageos.vmax.restapi.model.response.provisioning;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

//Refer StorageGroupVolumeListResponse
public class ResultList
{
    @SerializedName("to")
    @JsonProperty(value = "to")
    private String to;

    @SerializedName("result")
    @JsonProperty(value = "result")
    private Result[] result;

    @SerializedName("from")
    @JsonProperty(value = "from")
    private String from;

    public String getTo()
    {
        return to;
    }

    public void setTo(String to)
    {
        this.to = to;
    }

    public Result[] getResult()
    {
        return result;
    }

    public void setResult(Result[] result)
    {
        this.result = result;
    }

    public String getFrom()
    {
        return from;
    }

    public void setFrom(String from)
    {
        this.from = from;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
