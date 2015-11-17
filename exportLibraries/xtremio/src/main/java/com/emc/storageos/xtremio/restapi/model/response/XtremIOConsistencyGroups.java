package com.emc.storageos.xtremio.restapi.model.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_consistency_groups")

public class XtremIOConsistencyGroups {

    @SerializedName("consistency-groups")
    @JsonProperty(value = "consistency-groups")
    private XtremIOObjectInfo[] consistencyGroups;

    public XtremIOObjectInfo[] getConsitencyGroupsInfo() {
        return consistencyGroups != null ? consistencyGroups.clone() : consistencyGroups;
    }

    public void setConsistencyGroupsInfo(XtremIOObjectInfo[] volumeInfo) {
        if (consistencyGroups != null) {
            this.consistencyGroups = consistencyGroups.clone();
        }
    
    }
}
