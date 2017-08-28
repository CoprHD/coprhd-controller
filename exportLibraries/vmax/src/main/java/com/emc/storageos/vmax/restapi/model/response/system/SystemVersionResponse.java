/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.system;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/*
https://{unipshere-ip}:{portnumber}/univmax/restapi/system/version

{
  "version" : "..."
}

*/

@JsonRootName(value = "getVersionResult ")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemVersionResponse {
    @SerializedName("version")
    @JsonProperty(value = "version")
    private String version;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
