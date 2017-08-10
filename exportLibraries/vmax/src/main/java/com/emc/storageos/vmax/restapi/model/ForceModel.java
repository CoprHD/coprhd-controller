/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class ForceModel {
    @SerializedName("force")
    @JsonProperty(value = "force")
    private boolean force;

    @SerializedName("symforce")
    @JsonProperty(value = "symforce")
    private boolean symforce;

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isSymforce() {
        return symforce;
    }

    public void setSymforce(boolean symforce) {
        this.symforce = symforce;
    }
}
