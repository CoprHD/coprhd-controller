/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import org.codehaus.jackson.annotate.JsonProperty;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "vdc_capacities")
public class VDCCapacities {

    private List<VirtualArrayVirtualPoolCapacity> arrayCapacities;

    public VDCCapacities() {
    }

    public VDCCapacities(List<VirtualArrayVirtualPoolCapacity> arrayCapacities) {
        this.arrayCapacities = arrayCapacities;
    }

    @XmlElementWrapper(name = "varrays")
    /**
     * The list of Virtual Pool capacity
     * attributes of a Virtual Array. 
     */
    @XmlElement(name = "varray")
    @JsonProperty("varrays")
    public List<VirtualArrayVirtualPoolCapacity> getArrayCapacities() {
        if (arrayCapacities == null) {
            arrayCapacities = new ArrayList<VirtualArrayVirtualPoolCapacity>();
        }
        return arrayCapacities;
    }

    public void setArrayCapacities(
            List<VirtualArrayVirtualPoolCapacity> arrayCapacities) {
        this.arrayCapacities = arrayCapacities;
    }

}
