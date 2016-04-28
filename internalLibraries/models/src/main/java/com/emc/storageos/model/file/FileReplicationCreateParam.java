/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Specifies the parameters to change the virtual pool for a file system.
 */
@XmlRootElement(name = "filesystem_replication_create")
public class FileReplicationCreateParam {

    private String copyName;
    private String type;

    public FileReplicationCreateParam() {
    }

    public FileReplicationCreateParam(String copyName) {
        this.copyName = copyName;
    }

    public FileReplicationCreateParam(String copyName, String type) {
        this.copyName = copyName;
        this.type = type;
    }

    /**
     * Name of the mirror file system.
     * 
     */
    @XmlElement(name = "copy_name")
    @JsonProperty("copy_name")
    public String getCopyName() {
        return copyName;
    }

    public void setCopyName(String copyName) {
        this.copyName = copyName;
    }

    /**
     * File replication type
     * 
     */
    @XmlElement(name = "type")
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
