/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Snap extends VNXeBase {
    private String name;
    private String description;
    private VNXeBase lun;
    private VNXeBase parentSnap;
    private String creationTime;
    private String expirationTime;
    private int creatorType;
    private VNXeBase creatorUser;
    private String lastWritableTime;
    private Boolean isModified;
    private Boolean isAutoDelete;
    private long size;
    private String lastPromoteTime;
    private VNXeBase storageResource;
    private VNXeBase snapGroup;
    private Boolean isSystemSnap;
    private int accessType;
    private Boolean isReadOnly;
    private int state;
    private String attachedWWN;

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

    public VNXeBase getLun() {
        return lun;
    }

    public void setLun(VNXeBase lun) {
        this.lun = lun;
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

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getLastPromoteTime() {
        return lastPromoteTime;
    }

    public void setLastPromoteTime(String lastPromoteTime) {
        this.lastPromoteTime = lastPromoteTime;
    }

    public VNXeBase getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(VNXeBase storageResource) {
        this.storageResource = storageResource;
    }

    public VNXeBase getSnapGroup() {
        return snapGroup;
    }

    public void setSnapGroup(VNXeBase snapGroup) {
        this.snapGroup = snapGroup;
    }

    public String getAttachedWWN() {
        return attachedWWN;
    }

    public void setPromotedWWN(String attachedWWN) {
        this.attachedWWN = attachedWWN;
    }

    public Boolean getIsSystemSnap() {
        return isSystemSnap;
    }

    public void setIsSystemSnap(Boolean isSystemSnap) {
        this.isSystemSnap = isSystemSnap;
    }

    public int getAccessType() {
        return accessType;
    }

    public void setAccessType(int accessType) {
        this.accessType = accessType;
    }

    public Boolean getIsReadOnly() {
        return isReadOnly;
    }

    public void setIsReadOnly(Boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

}
