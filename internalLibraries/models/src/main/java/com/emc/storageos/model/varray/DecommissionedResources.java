/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "decommissioned_resources")
public class DecommissionedResources {

    private List<DecommissionedResourceRep> resources;

    public DecommissionedResources() {
    }

    /**
     * A list of decommissioned resources
     * 
     */
    @XmlElement(name = "decommissioned_resource")
    public List<DecommissionedResourceRep> getResources() {
        if (resources == null) {
            resources = new ArrayList<DecommissionedResourceRep>();
        }
        return resources;
    }

    public void setResources(List<DecommissionedResourceRep> resources) {
        this.resources = resources;
    }

    public void addResource(DecommissionedResourceRep resource) {
        if (resources == null) {
            resources = new ArrayList<DecommissionedResourceRep>();
        }
        resources.add(resource);
    }

}
