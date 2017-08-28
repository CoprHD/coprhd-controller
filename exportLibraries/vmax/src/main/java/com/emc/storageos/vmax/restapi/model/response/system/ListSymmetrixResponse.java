/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.system;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/*
https://{unipshere-ip}:{portnumber}/univmax/restapi/system/symmetrix/

{
  "symmetrixId" : [ "...", ... ],
  "num_of_symmetrix_arrays" : ...,
  "success" : false,
  "message" : "..."
}

*/

@JsonRootName(value = "listSymmetrixResult")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListSymmetrixResponse {

    @SerializedName("symmetrixId")
    @JsonProperty(value = "symmetrixId")
    private List<String> symmetrixId;

    public List<String> getSymmetrixId() {
        return symmetrixId;
    }

    public void setSymmetrixId(List<String> symmetrixId) {
        this.symmetrixId = symmetrixId;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
