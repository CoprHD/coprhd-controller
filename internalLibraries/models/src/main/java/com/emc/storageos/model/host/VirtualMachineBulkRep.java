/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_virtual_machines")
public class VirtualMachineBulkRep extends BulkRestRep {
    private List<VirtualMachineRestRep> vms;

    /**
     * List of host objects that exist in ViPR.
     * 
     */
    @XmlElement(name = "virtual_machine")
    public List<VirtualMachineRestRep> getHosts() {
        if (vms == null) {
            vms = new ArrayList<VirtualMachineRestRep>();
        }
        return vms;
    }

    public void setHosts(List<VirtualMachineRestRep> cluster) {
        this.vms = cluster;
    }

    public VirtualMachineBulkRep() {
    }

    public VirtualMachineBulkRep(List<VirtualMachineRestRep> vms) {
        this.vms = vms;
    }

}
