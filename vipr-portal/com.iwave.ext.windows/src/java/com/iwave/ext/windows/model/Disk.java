/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class Disk implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final String ONLINE = "Online";
    
    private int number;
    private String diskId;
    private String type;
    private String status;
    private int path;
    private int target;
    private int lunId;
    private String locationPath;
    private Boolean currentReadOnlyState;
    private Boolean readOnly;
    private Boolean bootDisk;
    private Boolean pageFileDisk;
    private Boolean hibernationFileDisk;
    private Boolean crashdumpDisk;
    private Boolean clusteredDisk;
    private List<Volume> volumes;

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getDiskId() {
        return diskId;
    }

    public void setDiskId(String diskId) {
        this.diskId = diskId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPath() {
        return path;
    }

    public void setPath(int path) {
        this.path = path;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public int getLunId() {
        return lunId;
    }

    public void setLunId(int lunId) {
        this.lunId = lunId;
    }

    public String getLocationPath() {
        return locationPath;
    }

    public void setLocationPath(String locationPath) {
        this.locationPath = locationPath;
    }

    public Boolean getCurrentReadOnlyState() {
        return currentReadOnlyState;
    }

    public void setCurrentReadOnlyState(Boolean currentReadOnlyState) {
        this.currentReadOnlyState = currentReadOnlyState;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public Boolean getBootDisk() {
        return bootDisk;
    }

    public void setBootDisk(Boolean bootDisk) {
        this.bootDisk = bootDisk;
    }

    public Boolean getPageFileDisk() {
        return pageFileDisk;
    }

    public void setPageFileDisk(Boolean pageFileDisk) {
        this.pageFileDisk = pageFileDisk;
    }

    public Boolean getHibernationFileDisk() {
        return hibernationFileDisk;
    }

    public void setHibernationFileDisk(Boolean hibernationFileDisk) {
        this.hibernationFileDisk = hibernationFileDisk;
    }

    public Boolean getCrashdumpDisk() {
        return crashdumpDisk;
    }

    public void setCrashdumpDisk(Boolean crashdumpDisk) {
        this.crashdumpDisk = crashdumpDisk;
    }

    public Boolean getClusteredDisk() {
        return clusteredDisk;
    }

    public void setClusteredDisk(Boolean clusteredDisk) {
        this.clusteredDisk = clusteredDisk;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<Volume> volumes) {
        this.volumes = volumes;
    }
    
    public boolean isOnline() {
        return ONLINE.equalsIgnoreCase(getStatus());
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("number", number);
        builder.append("diskId", diskId);
        builder.append("type", type);
        builder.append("status", status);
        builder.append("path", path);
        builder.append("target", target);
        builder.append("lunId", lunId);
        builder.append("locationPath", locationPath);
        builder.append("currentReadOnlyState", currentReadOnlyState);
        builder.append("readOnly", readOnly);
        builder.append("bootDisk", bootDisk);
        builder.append("pageFileDisk", pageFileDisk);
        builder.append("hibernationFileDisk", hibernationFileDisk);
        builder.append("crashdumpDisk", crashdumpDisk);
        builder.append("volumes", volumes);
        return builder.toString();
    }
}
