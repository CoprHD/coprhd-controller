/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Consistency group creation parameters
 */
@XmlRootElement(name = "consistency_group_create")
public class BlockConsistencyGroupCreate {
    /**
     * Name of the block consistency group
     * 
     */
    private String name;

    /**
     * Related Project URI
     * 
     */
    private URI project;

    /**
     * Flag which says if backend Replication Group needs to be created or not.
     * By default it is set to true.
     * 
     */
    private Boolean arrayConsistency = Boolean.TRUE;

    public BlockConsistencyGroupCreate() {
    }

    public BlockConsistencyGroupCreate(String name, URI project) {
        this.name = name;
        this.project = project;
    }

    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "project")
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    /**
     * Flag which says if backend Replication Group needs to be created or not.
     *
     */
    @XmlElement(name = "array_consistency", defaultValue = "true")
    public Boolean getArrayConsistency() {
        return arrayConsistency;
    }

    public void setArrayConsistency(Boolean arrayConsistency) {
        this.arrayConsistency = arrayConsistency;
    }
}
