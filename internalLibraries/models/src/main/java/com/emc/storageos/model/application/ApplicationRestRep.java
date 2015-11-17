/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.application;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "application")
public class ApplicationRestRep extends DataObjectRestRep{
    private String description;
    private RelatedResourceRep project;
    private RelatedResourceRep tenant;
    private List<RelatedResourceRep> volumes;
    
    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
    @XmlElementWrapper(name = "volumes")
    /**
     * List of volumes in the application.
     */
    @XmlElement(name = "volume")
    public List<RelatedResourceRep> getVolumes() {
        if (volumes == null) {
            volumes = new ArrayList<RelatedResourceRep>();
        }
        return volumes;
    }

    public void setVolumes(List<RelatedResourceRep> volumes) {
        this.volumes = volumes;
    }
    
    /**
     * This application's project
     */
    @XmlElement
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }
    
    /**
     * This application's tenant
     * 
     */
    @XmlElement
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

}
