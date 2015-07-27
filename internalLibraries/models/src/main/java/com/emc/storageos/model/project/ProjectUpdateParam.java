/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "project_update")
@XmlType(name="ProjectUpdateParam")
public class ProjectUpdateParam {
 
    private String name;
    private String owner;

    public ProjectUpdateParam() {}
    
    public ProjectUpdateParam(String name) {
        this.name = name;
    }
    
    public ProjectUpdateParam(String name, String owner) {
        this.name = name;
        this.owner = owner;
    }

    /**
     * New name for the project
     * @valid any string within length limits
     */
    @XmlElement
    @Length(min = 2, max = 128) 
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Owner of the project
     * @valid An existing username in the tenant's authentication provider
     */
    @XmlElement
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
    
}
