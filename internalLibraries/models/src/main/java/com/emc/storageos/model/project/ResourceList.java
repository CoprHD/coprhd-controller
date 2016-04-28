/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import com.emc.storageos.model.TypedRelatedResourceRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "project_resources")
public class ResourceList {

    private List<TypedRelatedResourceRep> resources;

    public ResourceList() {
    }

    public ResourceList(List<TypedRelatedResourceRep> resources) {
        this.resources = resources;
    }

    /**
     * The list of resources associated with this project
     * 
     */
    @XmlElement(name = "project_resource")
    public List<TypedRelatedResourceRep> getResources() {
        if (resources == null) {
            resources = new ArrayList<TypedRelatedResourceRep>();
        }
        return resources;
    }

    public void setResources(List<TypedRelatedResourceRep> resources) {
        this.resources = resources;
    }

}
