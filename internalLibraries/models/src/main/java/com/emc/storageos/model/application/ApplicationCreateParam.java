/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.application;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Application creation parameters
 */
@XmlRootElement(name = "application_create")
public class ApplicationCreateParam {
    private String name;
    private String description;
    private URI project;
    
    public ApplicationCreateParam() {
    }
    
    public ApplicationCreateParam(String name, URI project, String description) {
        this.name = name;
        this.description = description;
        this.project = project;
    }
    
    /**
     * Application unique name
     * 
     * @valid minimum of 2 characters
     * @valid maximum of 128 characters
     */
    @XmlElement(required = true)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Application description
     */
    @XmlElement
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * The ViPR project to which the application will belong.
     * 
     * @valid example: a valid URI of a ViPR project
     */
    @XmlElement(required = true)
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

}
