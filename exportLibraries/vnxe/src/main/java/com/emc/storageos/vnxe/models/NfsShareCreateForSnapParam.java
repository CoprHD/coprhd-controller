/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class NfsShareCreateForSnapParam extends ParamBase {
    private String path;
    private VNXeBase filesystemSnap;
    private VNXeBase snap; // argument name changed in latest firmware
    private String description;
    private Boolean isReadOnly;
    private int defaultAccess;
    private List<VNXeBase> noAccessHosts;
    private List<VNXeBase> readOnlyHosts;
    private List<VNXeBase> readWriteHosts;
    private List<VNXeBase> rootAccessHosts;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public VNXeBase getFilesystemSnap() {
        return filesystemSnap;
    }

    public void setFilesystemSnap(VNXeBase filesystemSnap) {
        this.filesystemSnap = filesystemSnap;
    }

    public VNXeBase getSnap() {
        return snap;
    }

    public void setSnap(VNXeBase snap) {
        this.snap = snap;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(Boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public int getDefaultAccess() {
        return defaultAccess;
    }

    public void setDefaultAccess(int defaultAccess) {
        this.defaultAccess = defaultAccess;
    }

    public List<VNXeBase> getNoAccessHosts() {
        return noAccessHosts;
    }

    public void setNoAccessHosts(List<VNXeBase> noAccessHosts) {
        this.noAccessHosts = noAccessHosts;
    }

    public List<VNXeBase> getReadOnlyHosts() {
        return readOnlyHosts;
    }

    public void setReadOnlyHosts(List<VNXeBase> readOnlyHosts) {
        this.readOnlyHosts = readOnlyHosts;
    }

    public List<VNXeBase> getReadWriteHosts() {
        return readWriteHosts;
    }

    public void setReadWriteHosts(List<VNXeBase> readWriteHosts) {
        this.readWriteHosts = readWriteHosts;
    }

    public List<VNXeBase> getRootAccessHosts() {
        return rootAccessHosts;
    }

    public void setRootAccessHosts(List<VNXeBase> rootAccessHosts) {
        this.rootAccessHosts = rootAccessHosts;
    }

}
