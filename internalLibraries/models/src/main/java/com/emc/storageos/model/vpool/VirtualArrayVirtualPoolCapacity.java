/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class VirtualArrayVirtualPoolCapacity {

    private URI id;
    private List<VirtualPoolCapacity> vpoolCapacities;

    public VirtualArrayVirtualPoolCapacity() {
    }

    public VirtualArrayVirtualPoolCapacity(URI id,
            List<VirtualPoolCapacity> vpoolCapacities) {
        this.id = id;
        this.vpoolCapacities = vpoolCapacities;
    }

    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    /**
     * A list of virtual pool capacity response instances.
     * 
     */
    @XmlElement(name = "varray_vpool_capacity")
    public List<VirtualPoolCapacity> getVpoolCapacities() {
        if (vpoolCapacities == null) {
            vpoolCapacities = new ArrayList<VirtualPoolCapacity>();
        }
        return vpoolCapacities;
    }

    public void setVpoolCapacities(List<VirtualPoolCapacity> vpoolCapacities) {
        this.vpoolCapacities = vpoolCapacities;
    }

}
