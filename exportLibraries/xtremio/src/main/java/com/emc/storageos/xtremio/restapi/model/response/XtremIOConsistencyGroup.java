package com.emc.storageos.xtremio.restapi.model.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_consistency_group")
public class XtremIOConsistencyGroup {
    
    @SerializedName("name")
    @JsonProperty(value="name")
    private String name;
    
    @SerializedName("num-of-vols")
    @JsonProperty(value="num-of-vols")
    private String numOfVols;
    
    @SerializedName("vol-list")
    @JsonProperty(value="vol-list")
    private List<List<Object>> volList;
    
    @SerializedName("tag-list")
    @JsonProperty(value="tag-list")
    private List<List<Object>> tagList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumOfVols() {
        return numOfVols;
    }

    public void setNumOfVols(String numOfVols) {
        this.numOfVols = numOfVols;
    }

    public List<List<Object>> getVolList() {
        return volList;
    }

    public void setVolList(List<List<Object>> volList) {
        this.volList = volList;
    }

    public List<List<Object>> getTagList() {
        return tagList;
    }

    public void setTagList(List<List<Object>> tagList) {
        this.tagList = tagList;
    }
    
}