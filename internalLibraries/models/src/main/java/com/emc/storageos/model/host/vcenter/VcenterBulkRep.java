/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_vcenters")
public class VcenterBulkRep extends BulkRestRep {
    private List<VcenterRestRep> vcenters;

    public VcenterBulkRep() {
    }

    public VcenterBulkRep(List<VcenterRestRep> vcenters) {
        this.vcenters = vcenters;
    }

    /**
     * List of vCenter objects that exists in ViPR.
     * 
     * @valid none
     */
    @XmlElement(name = "vcenter")
    public List<VcenterRestRep> getVcenters() {
        if (vcenters == null) {
            vcenters = new ArrayList<VcenterRestRep>();
        }
        return vcenters;
    }

    public void setVcenters(List<VcenterRestRep> cluster) {
        this.vcenters = cluster;
    }

}
