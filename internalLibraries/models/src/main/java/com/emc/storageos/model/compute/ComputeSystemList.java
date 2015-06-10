/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "compute_systems")
public class ComputeSystemList {
    private List<NamedRelatedResourceRep> computeSystems;

    public ComputeSystemList() {}
    
    public ComputeSystemList(List<NamedRelatedResourceRep> computeSystems) {
        this.computeSystems = computeSystems;
    }

    /**
     * List of compute system URLs with name
     * 
     * @valid none
     */ 
    @XmlElement(name = "compute_system")
    public List<NamedRelatedResourceRep> getComputeSystems() {
        if (computeSystems == null) {
            computeSystems = new ArrayList<NamedRelatedResourceRep>();
        }
        return computeSystems;
    }

    public void setComputeSystems(List<NamedRelatedResourceRep> computeSystems) {
        this.computeSystems = computeSystems;
    }
}
