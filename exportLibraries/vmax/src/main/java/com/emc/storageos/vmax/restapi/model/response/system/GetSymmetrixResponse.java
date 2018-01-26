/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.system;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.emc.storageos.vmax.restapi.model.Symmetrix;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/*
https://{unipshere-ip}:{portnumber}/univmax/restapi/system/symmetrix/{array-serailnumber}

{
  "symmetrix" : [ {
    "symmetrixId" : "...",
    "device_count" : ...,
    "ucode" : "...",
    "model" : "...",
    "local" : false
  }, ... ],
  "success" : false,
  "message" : "..."
}

*/

@JsonRootName(value = "getSymmetrixResult")
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetSymmetrixResponse {

    @SerializedName("symmetrix")
    @JsonProperty(value = "symmetrix")
    private List<Symmetrix> symmetrix;

    @SerializedName("success")
    @JsonProperty(value = "success")
    private boolean success;

    public List<Symmetrix> getSymmetrix() {
        return symmetrix;
    }

    public void setSymmetrix(List<Symmetrix> symmetrix) {
        this.symmetrix = symmetrix;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
