/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "copy")
public class Copy implements Serializable {

    private static final long serialVersionUID = -8250892549720042299L;
    private URI copyID;
    private String type;
    private FileSystemReplicationSettings replicationSettingParam;

    /**
     * @return the copyID
     */
    @XmlElement(name = "copyID", required = false)
    public URI getCopyID() {
        return copyID;
    }

    public void setCopyID(URI copyID) {
        this.copyID = copyID;
    }

    /**
     * Type of protection.
     *
     */
    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlElement(name = "replication_settings")
    public FileSystemReplicationSettings getReplicationSettingParam() {
        return replicationSettingParam;
    }

    public void setReplicationSettingParam(FileSystemReplicationSettings replicationSettingParam) {
        this.replicationSettingParam = replicationSettingParam;
    }

}
