/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Application update parameters
 */
@XmlRootElement(name = "application_update")
public class ApplicationUpdateParam {
    private String name;
    private String description;

    /**
     * Application unique name
     * 
     * @valid minimum of 2 characters
     * @valid maximum of 128 characters
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
     * Application description
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
