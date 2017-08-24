/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "replication_topology")
public class FileReplicationTopologyParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private URI sourceVArray;

    private Set<URI> targetVArrays;

    public FileReplicationTopologyParam() {
        super();
    }

    @XmlElement(name = "source_varray")
    public URI getSourceVArray() {
        return sourceVArray;
    }

    public void setSourceVArray(URI srcVArray) {
        this.sourceVArray = srcVArray;
    }

    @XmlElementWrapper(name = "target_varrays")
    @XmlElement(name = "target_varray")
    public Set<URI> getTargetVArrays() {
        return targetVArrays;
    }

    public void setTargetVArrays(Set<URI> targetVArrays) {
        this.targetVArrays = targetVArrays;
    }
}
