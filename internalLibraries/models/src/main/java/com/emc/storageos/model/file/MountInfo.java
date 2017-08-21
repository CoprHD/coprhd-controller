/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Holds the mount attributes for operations
 * 
 * @author yelkaa
 * 
 */
public class MountInfo {
    private URI hostId;
    private URI fsId;
    private String mountPath;
    private String subDirectory;
    private String securityType;

    @XmlElement(name = "host")
    @JsonProperty("host")
    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    @XmlElement(name = "filesystem")
    @JsonProperty("filesystem")
    public URI getFsId() {
        return fsId;
    }

    public void setFsId(URI fsId) {
        this.fsId = fsId;
    }

    @XmlElement(name = "mount_path")
    @JsonProperty("mount_path")
    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    @XmlElement(name = "sub_directory")
    @JsonProperty("sub_directory")
    public String getSubDirectory() {
        return subDirectory;
    }

    public void setSubDirectory(String subDirectory) {
        this.subDirectory = subDirectory;
    }

    @XmlElement(name = "security_type")
    @JsonProperty("security_type")
    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }

    public String fetchMountString() {
        StringBuffer strMount = new StringBuffer();

        String subDirPath = "";
        if (getSubDirectory() != null && !getSubDirectory().isEmpty()) {
            subDirPath = "/" + getSubDirectory();
        }
        strMount.append(getHostId()).append(";")
                .append(getFsId()).append(";")
                .append(getSecurityType()).append(";")
                .append(getMountPath()).append(";")
                .append(subDirPath);

        return strMount.toString();
    }
}