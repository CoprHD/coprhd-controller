/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "project_create")
public class ProjectParam {

    private String name;

    public ProjectParam() {
    }

    public ProjectParam(String name) {
        this.name = name;
    }

    /**
     * Name of the project
     * 
     * @valid any string within length limits
     */
    @XmlElement(required = true)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
