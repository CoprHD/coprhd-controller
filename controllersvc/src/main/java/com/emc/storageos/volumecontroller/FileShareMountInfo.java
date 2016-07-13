/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;

import com.emc.storageos.model.file.FileSystemMountParam;

/**
 * Place holder for FS QuotaDirectory information.
 * 
 * @author yelkaa
 * 
 */
@SuppressWarnings("serial")
public class FileShareMountInfo implements Serializable {

    private String type; // nfs, cifs
    private URI host;
    private String subDir;
    private String security;
    private String path;

    public FileShareMountInfo(FileSystemMountParam mInfo) {
        this.type = mInfo.getType();
        this.host = mInfo.getHost();
        this.subDir = mInfo.getSubDir();
        this.security = mInfo.getSecurity();
        this.path = mInfo.getPath();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public URI getHost() {
        return host;
    }

    public void setHost(URI host) {
        this.host = host;
    }

    public String getSubDir() {
        return subDir;
    }

    public void setSubDir(String subDir) {
        this.subDir = subDir;
    }

    public String getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = security;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

}
