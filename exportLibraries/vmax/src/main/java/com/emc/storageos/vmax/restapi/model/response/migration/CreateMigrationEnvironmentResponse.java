/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.migration;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class CreateMigrationEnvironmentResponse {
    @SerializedName("arrayId")
    @JsonProperty(value = "arrayId")
    private String arrayId;

    @SerializedName("storageGroupCount")
    @JsonProperty(value = "storageGroupCount")
    private int storageGroupCount;

    @SerializedName("migrationSessionCount")
    @JsonProperty(value = "migrationSessionCount")
    private int migrationSessionCount;

    @SerializedName("local")
    @JsonProperty(value = "local")
    private boolean local;

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

    public String getArrayId() {
        return arrayId;
    }

    public void setArrayId(String arrayId) {
        this.arrayId = arrayId;
    }

    public int getStorageGroupCount() {
        return storageGroupCount;
    }

    public void setStorageGroupCount(int storageGroupCount) {
        this.storageGroupCount = storageGroupCount;
    }

    public int getMigrationSessionCount() {
        return migrationSessionCount;
    }

    public void setMigrationSessionCount(int migrationSessionCount) {
        this.migrationSessionCount = migrationSessionCount;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

}
