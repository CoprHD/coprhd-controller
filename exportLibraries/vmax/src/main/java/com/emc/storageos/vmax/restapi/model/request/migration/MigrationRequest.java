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

    @SerializedName("otherArrayId")
    @JsonProperty(value = "otherArrayId")
    private String otherArrayId;

    @SerializedName("noCompression")
    @JsonProperty(value = "noCompression")
    private boolean noCompression;

    @SerializedName("srpId")
    @JsonProperty(value = "srpId")
    private String srpId;

    @SerializedName("action")
    @JsonProperty(value = "action")
    private String action;

    @SerializedName("sync")
    @JsonProperty(value = "sync")
    private SyncModel sync;

    public String getOtherArrayId() {
        return otherArrayId;
    }

    public void setOtherArrayId(String otherArrayId) {
        this.otherArrayId = otherArrayId;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

    public boolean isNoCompression() {
        return noCompression;
    }

    public void setNoCompression(boolean noCompression) {
        this.noCompression = noCompression;
    }

    public String getSrpId() {
        return srpId;
    }

    public void setSrpId(String srpId) {
        this.srpId = srpId;
    }

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

}
