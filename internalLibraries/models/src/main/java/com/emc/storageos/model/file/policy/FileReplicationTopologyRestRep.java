/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "replication_topology")
public class FileReplicationTopologyRestRep implements Serializable {

    private static final long serialVersionUID = 1L;

    private NamedRelatedResourceRep sourceVArray;

    private Set<NamedRelatedResourceRep> targetVArrays;

    public FileReplicationTopologyRestRep() {
        super();
    }

    @XmlElement(name = "source_varray")
    public NamedRelatedResourceRep getSourceVArray() {
        return sourceVArray;
    }

    public void setSourceVArray(NamedRelatedResourceRep srcVArray) {
        this.sourceVArray = srcVArray;
    }

    @XmlElementWrapper(name = "target_varrays")
    @XmlElement(name = "target_varray")
    public Set<NamedRelatedResourceRep> getTargetVArrays() {
        return targetVArrays;
    }

    public void setTargetVArrays(Set<NamedRelatedResourceRep> targetVArrays) {
        this.targetVArrays = targetVArrays;
    }

    public void addTargetVArray(NamedRelatedResourceRep targetVArray) {
        if (this.targetVArrays == null) {
            this.targetVArrays = new HashSet<NamedRelatedResourceRep>();
        }
        this.targetVArrays.add(targetVArray);
    }
}
