/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.protection;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "protection_set_resources")
public class ProtectionResourcesList {

    private List<NamedRelatedResourceRep> resources;

    public ProtectionResourcesList() {
    }

    public ProtectionResourcesList(List<NamedRelatedResourceRep> resources) {
        this.resources = resources;
    }

    /**
     * List of Protection Set Resources. Not currently being used.
     * 
     * @valid none - not currently implemented
     */
    @XmlElement(name = "protection_set_resource")
    public List<NamedRelatedResourceRep> getResources() {
        if (resources == null) {
            resources = new ArrayList<NamedRelatedResourceRep>();
        }
        return resources;
    }

    public void setResources(List<NamedRelatedResourceRep> resources) {
        this.resources = resources;
    }

}
