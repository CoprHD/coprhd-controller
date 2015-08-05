package com.emc.storageos.xtremio.restapi.model.response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value="xtremio_xms_response")
public class XtremIOXMSResponse {
    
    @SerializedName("content")
    @JsonProperty(value = "content")
    private XtremIOXMS content;

    public XtremIOXMS getContent() {
        return content;
    }

    public void setContent(XtremIOXMS content) {
        this.content = content;
    }
}
