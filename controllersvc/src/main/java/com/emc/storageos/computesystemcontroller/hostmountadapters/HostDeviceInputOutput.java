/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;

/**
 * 
 * @author yelkaa
 *
 */
public class HostDeviceInputOutput {
    private URI resId; // Id of the associated resource like fs or vol
    private URI hostId;
    private String type; // cifs, nfs
    private String mountPath;
    private String mountPoint;
    private String security;
    private String subDirectory;
    private String fsType;
    private Boolean isNFSv4;

    public URI getResId() {
        return resId;
    }

    public void setResId(URI resId) {
        this.resId = resId;
    }

    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    public String getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = security;
    }

    public String getSubDirectory() {
        return subDirectory;
    }

    public void setSubDirectory(String subDirectory) {
        this.subDirectory = subDirectory;
    }

    public String getFsType() {
        return fsType;
    }

    public void setFsType(String fsType) {
        this.fsType = fsType;
    }

    public Boolean getIsNFSv4() {
        return isNFSv4;
    }

    public void setIsNFSv4(Boolean isNFSv4) {
        this.isNFSv4 = isNFSv4;
    }
}
