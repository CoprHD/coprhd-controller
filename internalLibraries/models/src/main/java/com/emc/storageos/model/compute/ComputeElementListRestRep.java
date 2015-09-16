/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "compute_elements")
public class ComputeElementListRestRep {

    private List<ComputeElementRestRep> list;

    public ComputeElementListRestRep() {
    }

    public ComputeElementListRestRep(List<ComputeElementRestRep> list) {
        this.list = list;
    }

    @XmlElement(name = "compute_element")
    public List<ComputeElementRestRep> getList() {
        if (list == null) {
            list = new ArrayList<ComputeElementRestRep>();
        }
        return list;
    }

    public void setList(List<ComputeElementRestRep> list) {
        this.list = list;
    }

}
