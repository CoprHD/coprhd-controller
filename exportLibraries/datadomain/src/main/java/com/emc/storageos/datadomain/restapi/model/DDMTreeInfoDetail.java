/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import java.util.List;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value = "mtree")
public class DDMTreeInfoDetail {

    public String id;

    public String name;

    // 0: false; 1: true
    // deletable: false when there is a protocol attached on this mtree
    public Boolean deletable;

    // delete status: 0: not deleted; 1: deleted
    @SerializedName("del_status")
    @JsonProperty(value = "del_status")
    public Integer delStatus;

    // read only status: 0: RW; 1: RO
    @SerializedName("ro_status")
    @JsonProperty(value = "ro_status")
    public Integer roStatus;

    // replication destination; true: replication destination. Default value is false.
    @SerializedName("repl_destination")
    @JsonProperty(value = "repl_destination")
    public Boolean replDestination;

    // retention-lock status: 0: never enabled; 1: currently enabled; 2: previously enabled
    @SerializedName("rl_status")
    @JsonProperty(value = "rl_status")
    public Integer rlStatus;

    // retention-lock mode: 0: no mode; governance mode: 1; compliance mode: 2
    @SerializedName("rl_mode")
    @JsonProperty(value = "rl_mode")
    public Integer rlMode;

    @SerializedName("tenant")
    @JsonProperty(value = "tenant")
    public String tenant;

    @SerializedName("tenant_unit")
    @JsonProperty(value = "tenant_unit")
    public String tenantUnit;

    @SerializedName("physical_capacity")
    @JsonProperty(value = "physical_capacity")
    public DDCapacity physicalCapacity;

    @SerializedName("logical_capacity")
    @JsonProperty(value = "logical_capacity")
    public DDCapacity logicalCapacity;

    @SerializedName("quota_config")
    @JsonProperty(value = "quota_config")
    public DDQuotaConfig quotaConfig;

    // Protocol Configured-value list [CIFS,NFS,DD Boost,VTL,vDisk]
    @SerializedName("protocol_config")
    @JsonProperty(value = "protocol_config")
    public List<String> protocolName;

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}
