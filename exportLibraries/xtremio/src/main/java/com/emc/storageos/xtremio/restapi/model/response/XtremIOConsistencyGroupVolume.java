/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_consistency_groups_volumes")

public class XtremIOConsistencyGroupVolume {

    @SerializedName("consistency-groups")
    @JsonProperty(value = "consistency-groups")
    private XtremIOObjectInfo[] consistencyGroups;

    public XtremIOObjectInfo[] getConsitencyGroups() {
        return consistencyGroups != null ? consistencyGroups.clone() : consistencyGroups;
    }

    public void setConsistencyGroups(XtremIOObjectInfo[] consistencyGroups) {
        if (consistencyGroups != null) {
            this.consistencyGroups = consistencyGroups.clone();
        }
    
    }
}
