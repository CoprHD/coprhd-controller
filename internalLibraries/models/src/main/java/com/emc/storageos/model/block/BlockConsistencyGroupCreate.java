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
}
