/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Specifies the parameters to change the virtual pool for a file system.
 */
@XmlRootElement(name = "filesystem_vpool_change")
public class FileSystemVirtualPoolChangeParam {

    private URI virtualPool;

    public FileSystemVirtualPoolChangeParam() {
    }

    public FileSystemVirtualPoolChangeParam(URI virtualPool) {
        this.virtualPool = virtualPool;
    }

    /**
     * ID of the new virtual pool.
     * 
     * 
     */
    @XmlElement(required = true, name = "vpool")
    @JsonProperty("vpool")
    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
    }
}
