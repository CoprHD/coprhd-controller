/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;


import com.google.gson.annotations.SerializedName;


@JsonRootName(value="xtremio_folder_response")
public class XtremIOIGFolderResponse {
    
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOIGFolder content;

    public XtremIOIGFolder getContent() {
        return content;
    }

    public void setContent(XtremIOIGFolder content) {
        this.content = content;
    }

   

   

}
