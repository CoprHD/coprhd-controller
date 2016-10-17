/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 * @author yelkaa
 *
 */
@XmlRootElement(name = "unmount_export")
public class FileSystemUnmountParam {

    private URI hostId;
    private String mountPath;

    public FileSystemUnmountParam() {

    }

    public FileSystemUnmountParam(URI hostId, String mountPath) {
        this.hostId = hostId;
        this.mountPath = mountPath;
    }

    @XmlElement(name = "host")
    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    @XmlElement(name = "mount_path")
    public String getMountPath() {
        return mountPath;
    }

    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }
}
