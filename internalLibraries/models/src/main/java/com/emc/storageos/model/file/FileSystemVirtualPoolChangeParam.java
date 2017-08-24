/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Specifies the parameters to change the virtual pool for a file system.
 */
@XmlRootElement(name = "filesystem_vpool_change")
public class FileSystemVirtualPoolChangeParam {

    private URI virtualPool;
    private Set<URI> targetVArrays;
    private URI filePolicy;

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

    @XmlElementWrapper(name = "target_varrays")
    @XmlElement(name = "target_varray")
    public Set<URI> getTargetVArrays() {
        return targetVArrays;
    }

    public void setTargetVArrays(Set<URI> targetVArrays) {
        this.targetVArrays = targetVArrays;
    }

    @XmlElement(name = "file_policy")
    public URI getFilePolicy() {
        return filePolicy;
    }

    public void setFilePolicy(URI filePolicy) {
        this.filePolicy = filePolicy;
    }
}
