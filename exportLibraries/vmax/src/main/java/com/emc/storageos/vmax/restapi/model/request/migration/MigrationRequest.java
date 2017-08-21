/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.request.migration;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.vmax.restapi.model.ForceModel;
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

    @SerializedName("recover")
    @JsonProperty(value = "recover")
    private ForceModel recover;

    @SerializedName("cutover")
    @JsonProperty(value = "cutover")
    private ForceModel cutover;

    @SerializedName("executionOption")
    @JsonProperty(value = "executionOption")
    private String executionOption;

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

    public ForceModel getRecover() {
        return recover;
    }

    public void setRecover(ForceModel recover) {
        this.recover = recover;
    }

    public ForceModel getCutover() {
        return cutover;
    }

    public void setCutover(ForceModel cutover) {
        this.cutover = cutover;
    }

    public String getExecutionOption() {
        return executionOption;
    }

    public void setExecutionOption(String executionOption) {
        this.executionOption = executionOption;
    }

}
