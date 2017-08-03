/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.request.migration;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.vmax.restapi.model.SyncModel;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class MigrationRequest {

    @SerializedName("action")
    @JsonProperty(value = "action")
    private String action;

    @SerializedName("sync")
    @JsonProperty(value = "sync")
    private SyncModel sync;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public SyncModel getSync() {
        return sync;
    }

    public void setSync(SyncModel sync) {
        this.sync = sync;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
