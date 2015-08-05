package com.emc.storageos.xtremio.restapi.model.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.Joiner;
import com.google.gson.annotations.SerializedName;

public class XtremIOSnapshotSet {
    
    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;
    
    @SerializedName("num-of-vols")
    @JsonProperty(value = "num-of-vols")
    private String numOfVols;
    
    @SerializedName("cg-name")
    @JsonProperty(value = "cg-name")
    private String cgName;
    
    @SerializedName("vol-list")
    @JsonProperty(value = "vol-list")
    private List<List<Object>> volList;

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

    public String getCgName() {
        return cgName;
    }

    public void setCgName(String cgName) {
        this.cgName = cgName;
    }

    public List<List<Object>> getVolList() {
        return volList;
    }

    public void setVolList(List<List<Object>> volList) {
        this.volList = volList;
    }

    @Override
    public String toString() {
        return "XtremIOSnapshotSet [name=" + name + ", numOfVols=" + numOfVols + ", cgName=" + cgName + ", volList=" 
                + Joiner.on("; ").join(volList) + "]";
    }

}
