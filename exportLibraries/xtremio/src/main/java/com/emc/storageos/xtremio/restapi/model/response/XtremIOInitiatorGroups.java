/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_igs")
public class XtremIOInitiatorGroups {
    @SerializedName("content")
    @JsonProperty(value="content")
    private XtremIOInitiatorGroup content;

    public XtremIOInitiatorGroup getContent() {
        return content;
    }

    public void setContent(XtremIOInitiatorGroup content) {
        this.content = content;
    }

}
