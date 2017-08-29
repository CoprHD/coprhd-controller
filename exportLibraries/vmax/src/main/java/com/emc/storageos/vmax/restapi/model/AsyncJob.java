/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "job")
public class AsyncJob {

    @SerializedName("jobId")
    @JsonProperty(value = "jobId")
    private String jobId;

    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    @SerializedName("status")
    @JsonProperty(value = "status")
    private String status;

    @SerializedName("result")
    @JsonProperty(value = "result")
    private String result;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
