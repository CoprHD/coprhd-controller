package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

public class MountInfo {
    private URI hostId;
    private URI fsId;
    private String mountPath;
    private String subDirectory;
    private String securityType;
    private String tag;

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

    @XmlElement(name = "tag")
    @JsonProperty("tag")
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}