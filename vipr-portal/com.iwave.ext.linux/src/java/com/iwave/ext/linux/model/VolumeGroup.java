/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.util.List;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class VolumeGroup implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private List<String> logicalVolumes;
    private List<String> physicalVolumes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getLogicalVolumes() {
        return logicalVolumes;
    }

    public void setLogicalVolumes(List<String> logicalVolumes) {
        this.logicalVolumes = logicalVolumes;
    }

    public List<String> getPhysicalVolumes() {
        return physicalVolumes;
    }

    public void setPhysicalVolumes(List<String> physicalVolumes) {
        this.physicalVolumes = physicalVolumes;
    }

    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("name", name);
        builder.append("logicalVolumes", logicalVolumes);
        builder.append("physicalVolumes", physicalVolumes);
        return builder.toString();
    }
}
