/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_compute_elements")
public class ComputeElementBulkRep extends BulkRestRep {
    private List<ComputeElementRestRep> computeElements;

    public ComputeElementBulkRep() {
    }

    public ComputeElementBulkRep(List<ComputeElementRestRep> computeElements) {
        this.computeElements = computeElements;
    }

    /**
     * List of compute element objects that exist in ViPR.
     * @valid none
     */
    @XmlElement(name = "compute_element")
    public List<ComputeElementRestRep> getComputeElements() {
        if (computeElements == null) {
            computeElements = new ArrayList<ComputeElementRestRep>();
        }
        return computeElements;
    }

    public void setComputeElements(List<ComputeElementRestRep> computeElements) {
        this.computeElements = computeElements;
    }
}
