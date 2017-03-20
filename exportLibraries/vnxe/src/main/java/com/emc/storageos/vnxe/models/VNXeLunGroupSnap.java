/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeLunGroupSnap extends VNXeBase {

    private String name;
    private String description;
    private VNXeBase storageResource;
    private VNXeBase parentSnap;
    private String creationTime;
    private String expirationTime;
    private int creatorType;
    private VNXeBase creatorUser;
    private Boolean isAttached;
    private String lastWritableTime;
    private Boolean isModified;
    private Boolean isAutoDelete;
    private List<Integer> operationalStatus;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public VNXeBase getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(VNXeBase storageResource) {
        this.storageResource = storageResource;
    }

    public VNXeBase getParentSnap() {
        return parentSnap;
    }

    public void setParentSnap(VNXeBase parentSnap) {
        this.parentSnap = parentSnap;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(String creationTime) {
        this.creationTime = creationTime;
    }

    public String getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(String expirationTime) {
        this.expirationTime = expirationTime;
    }

    public int getCreatorType() {
        return creatorType;
    }

    public void setCreatorType(int creatorType) {
        this.creatorType = creatorType;
    }

    public VNXeBase getCreatorUser() {
        return creatorUser;
    }

    public void setCreatorUser(VNXeBase creatorUser) {
        this.creatorUser = creatorUser;
    }

    public Boolean getIsAttached() {
        return isAttached;
    }

    public void setIsAttached(Boolean isAttached) {
        this.isAttached = isAttached;
    }

    public String getLastWritableTime() {
        return lastWritableTime;
    }

    public void setLastWritableTime(String lastWritableTime) {
        this.lastWritableTime = lastWritableTime;
    }

    public Boolean getIsModified() {
        return isModified;
    }

    public void setIsModified(Boolean isModified) {
        this.isModified = isModified;
    }

    public Boolean getIsAutoDelete() {
        return isAutoDelete;
    }

    public void setIsAutoDelete(Boolean isAutoDelete) {
        this.isAutoDelete = isAutoDelete;
    }

    public List<Integer> getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(List<Integer> operationalStatus) {
        this.operationalStatus = operationalStatus;
    }
}
