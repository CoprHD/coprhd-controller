/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class SyncModel {

    @SerializedName("start")
    @JsonProperty(value = "start")
    private boolean start;

    @SerializedName("stop")
    @JsonProperty(value = "stop")
    private boolean stop;

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

    public boolean isStart() {
        return start;
    }

    public void setStart(boolean start) {
        this.start = start;
    }

}
