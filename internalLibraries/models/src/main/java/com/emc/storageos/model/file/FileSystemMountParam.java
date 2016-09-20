/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a file system mount,
 * specified during its creation.
 * 
 * @author yelkaa
 * 
 */
@XmlRootElement(name = "mount_filesystem")
public class FileSystemMountParam {

    private URI host;
    private String subDir;
    private String security;
    private String path;
    private String fsType = "auto";

    public FileSystemMountParam() {

    }

    public FileSystemMountParam(URI host, String subDir, String security, String path, String fsType) {
        this.host = host;
        this.subDir = subDir;
        this.security = security;
        this.path = path;
        this.fsType = fsType;
    }

    @XmlElement(name = "host", required = true)
    public URI getHost() {
        return host;
    }

    public void setHost(URI host) {
        this.host = host;
    }

    @XmlElement(name = "sub_directory")
    public String getSubDir() {
        return subDir;
    }

    public void setSubDir(String subDir) {
        this.subDir = subDir;
    }

    @XmlElement(name = "security", required = true)
    public String getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = security;
    }

    @XmlElement(name = "path", required = true)
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @XmlElement(name = "fs_type")
    public String getFsType() {
        return fsType;
    }

    public void setFsType(String fsType) {
        this.fsType = fsType;
    }
}
