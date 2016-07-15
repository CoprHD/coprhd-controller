/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Response for getting a list of tenant virtualMachines
 */
@XmlRootElement(name = "virtualMachines")
public class VirtualMachineList {
    private List<NamedRelatedResourceRep> virtualMachines;

    public VirtualMachineList() {
    }

    public VirtualMachineList(List<NamedRelatedResourceRep> virtualMachines) {
        this.virtualMachines = virtualMachines;
    }

    /**
     * List of virtualMachine objects that exist in ViPR. Each
     * virtualMachine contains an id, name, and link.
     * 
     */
    @XmlElement(name = "virtualMachine")
    public List<NamedRelatedResourceRep> getVirtualMachines() {
        if (virtualMachines == null) {
            virtualMachines = new ArrayList<NamedRelatedResourceRep>();
        }
        return virtualMachines;
    }

    public void setVirtualMachines(List<NamedRelatedResourceRep> virtualMachines) {
        this.virtualMachines = virtualMachines;
    }
}
