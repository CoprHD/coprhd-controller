/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_initiators_info")
public class XtremIOInitiatorsInfo {
    @SerializedName("initiators")
    @JsonProperty(value = "initiators")
    private XtremIOInitiatorInfo[] initiators;

    public XtremIOInitiatorInfo[] getInitiators() {
        return initiators != null ? initiators.clone() : initiators;
    }

    public void setInitiators(XtremIOInitiatorInfo[] initiators) {
        if (initiators != null) {
            this.initiators = initiators.clone();
        }
    }

}
