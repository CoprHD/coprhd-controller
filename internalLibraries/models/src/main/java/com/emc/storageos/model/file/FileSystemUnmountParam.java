/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "unmount_export")
public class FileSystemUnmountParam {

    private URI hostId;
    private String destinationPath;
    private String type;

    public FileSystemUnmountParam(URI hostId, String destinationPath, String type) {
        this.hostId = hostId;
        this.destinationPath = destinationPath;
        this.type = type;
    }

    @XmlElement(name = "hostId")
    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    @XmlElement(name = "destinationPath")
    public String getDestinationPath() {
        return destinationPath;
    }

    public void setDestinationPath(String destinationPath) {
        this.destinationPath = destinationPath;
    }

    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
