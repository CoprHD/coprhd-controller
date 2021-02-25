/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi.model.response;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.annotations.SerializedName;

@JsonRootName(value = "xtremio_lun_map")
public class XtremIOLunMap {

    @SerializedName("mapping-id")
    @JsonProperty(value = "mapping-id")
    private List<String> mappingInfo;

    @SerializedName("ig-name")
    @JsonProperty(value = "ig-name")
    private String igName;

    @SerializedName("vol-name")
    @JsonProperty(value = "vol-name")
    private String volumeName;

    @SerializedName("lun")
    @JsonProperty(value = "lun")
    private String lun;

    @SerializedName("name")
    @JsonProperty(value = "name")
    private String name;

    @SerializedName("vol-index")
    @JsonProperty(value = "vol-index")
    private String volumeIndex;

    public List<String> getMappingInfo() {
        return mappingInfo;
    }

    public void setMappingInfo(List<String> mappingInfo) {
        this.mappingInfo = mappingInfo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVolumeIndex() {
        return volumeIndex;
    }

    public void setVolumeIndex(String volumeIndex) {
        this.volumeIndex = volumeIndex;
    }

    public String getIgName() {
        return igName;
    }

    public void setIgName(String igName) {
        this.igName = igName;
    }

    public String getVolumeName() {
        return volumeName;
    }

    public void setVolumeName(String volumeName) {
        this.volumeName = volumeName;
    }

    public String getLun() {
        return lun;
    }

    public void setLun(String lun) {
        this.lun = lun;
    }

    @Override
    public String toString() {
        return "XtremIOLunMap [mappingInfo=" + mappingInfo + ", igName=" + igName + ", volumeName=" + volumeName
                + ", lun=" + lun + ", name=" + name + ", volumeIndex=" + volumeIndex + "]";
    }

}
