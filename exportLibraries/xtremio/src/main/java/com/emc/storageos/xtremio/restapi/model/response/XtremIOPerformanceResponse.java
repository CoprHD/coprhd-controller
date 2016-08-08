/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_performance_response")
public class XtremIOPerformanceResponse {

    @SerializedName("counters")
    @JsonProperty(value = "counters")
    // List of metrics for the Object entity
    private String[][] counters;

    @SerializedName("members")
    @JsonProperty(value = "members")
    // Array defining the attribute names in the counters (in order)
    private String[] members;

    public String[][] getCounters() {
        return counters != null ? counters.clone() : counters;
    }

    public void setCounters(String[][] counters) {
        if (counters != null) {
            this.counters = counters.clone();
        }
    }

    public String[] getMembers() {
        return members != null ? members.clone() : members;
    }

    public void setMembers(String[] members) {
        if (members != null) {
            this.members = members.clone();
        }
    }
}
