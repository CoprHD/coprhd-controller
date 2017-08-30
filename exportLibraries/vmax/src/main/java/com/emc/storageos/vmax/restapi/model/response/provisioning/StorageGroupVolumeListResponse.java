/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi.model.response.provisioning;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/*
 https://{unipshere-ip}:{portnumber}/univmax/restapi/84/provisioning/symmetrix/{id}/volume?storageGroupId={SG-id}

 <iterator>
 <resultList from="1" to="6">
 <result xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="VolumeResultType">
 <volumeId>004FA</volumeId>
 </result>
 <result xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="VolumeResultType">
 <volumeId>004FF</volumeId>
 </result>
 </resultList>
 </iterator>

 */

@JsonRootName(value = "iterator")
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageGroupVolumeListResponse {

    @SerializedName("resultList")
    @JsonProperty(value = "resultList")
    private ResultList resultList;

    @SerializedName("id")
    @JsonProperty(value = "id")
    private String id;

    public ResultList getResultList() {
        return resultList;
    }

    public void setResultList(ResultList resultList) {
        this.resultList = resultList;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
