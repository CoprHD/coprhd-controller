/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import java.util.List;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value = "system_info")
public class DDSystem {

    public String id;

    public String name;

    public String version;

    @SerializedName("serialno")
    @JsonProperty(value = "serialno")
    public String serialNo;

    public String model;

    @SerializedName("uptime")
    @JsonProperty(value = "uptime")
    public String upTime;

    public String state;

    public String status;

    @SerializedName("added_epoch")
    @JsonProperty(value = "added_epoch")
    public Integer addedEpoch;

    @SerializedName("hd_sync_epoch")
    @JsonProperty(value = "hd_sync_epoch")
    public Integer hdSyncEpoch;

    @SerializedName("cd_sync_epoch")
    @JsonProperty(value = "cd_sync_epoch")
    public Integer cdSyncEpoch;

    @SerializedName("admin_host")
    @JsonProperty(value = "admin_host")
    public String adminHost;

    @SerializedName("admin_email")
    @JsonProperty(value = "admin_email")
    public String adminEmail;

    @SerializedName("mem_size")
    @JsonProperty(value = "mem_size")
    public Long memSize;

    @SerializedName("partition_size")
    @JsonProperty(value = "partition_size")
    public String partitionSize;

    @SerializedName("time_zone")
    @JsonProperty(value = "time_zone")
    public String timeZone;

    @SerializedName("physical_capacity")
    @JsonProperty(value = "physical_capacity")
    public DDCapacity physicalCapacity;

    @SerializedName("logical_capacity")
    @JsonProperty(value = "logical_capacity")
    public DDCapacity logicalCapacity;

    @SerializedName("subscribed_capacity")
    @JsonProperty(value = "subscribed_capacity")
    public long subscribedCapacity;

    @SerializedName("compression_factor")
    @JsonProperty(value = "compression_factor")
    public double compressionFactor;

    public List<DDLicenseInfo> license;

    public String location;

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
