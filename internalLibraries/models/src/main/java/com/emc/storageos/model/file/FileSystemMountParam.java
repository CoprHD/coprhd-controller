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

    private String type; // nfs, cifs
    private URI host;
    private String subDir;
    private String security;
    private String path;

    public FileSystemMountParam(String type, URI host, String subDir, String security, String path) {
        this.type = type;
        this.host = host;
        this.subDir = subDir;
        this.security = security;
        this.path = path;
    }

    @XmlElement(name = "type", required = true)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlElement(name = "host", required = true)
    public URI getHost() {
        return host;
    }

    public void setHost(URI host) {
        this.host = host;
    }

    @XmlElement(name = "subdir")
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

}
