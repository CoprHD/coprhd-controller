/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by zeldib on 2/10/14.
 */
public class DDLicenseInfo {

    @SerializedName("license_key")
    @JsonProperty(value="license_key")
    public String licenseKey;

    public String feature;

    public String toString() {
        return new Gson().toJson(this).toString();
    }


}
