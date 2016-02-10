/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "compute_elements")
public class ComputeElementList {
    private List<NamedRelatedResourceRep> computeElements;

    public ComputeElementList() {
    }

    public ComputeElementList(List<NamedRelatedResourceRep> computeElements) {
        this.computeElements = computeElements;
    }

    /**
     * List of compute element URLs with name
     * 
     */
    @XmlElement(name = "compute_element")
    public List<NamedRelatedResourceRep> getComputeElements() {
        if (computeElements == null) {
            computeElements = new ArrayList<NamedRelatedResourceRep>();
        }
        return computeElements;
    }

    public void setComputeElements(List<NamedRelatedResourceRep> computeElements) {
        this.computeElements = computeElements;
    }
}
