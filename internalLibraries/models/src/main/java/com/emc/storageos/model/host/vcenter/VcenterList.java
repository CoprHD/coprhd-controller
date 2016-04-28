/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for getting a list of tenant vCenters
 */
@XmlRootElement(name = "vcenters")
public class VcenterList {
    private List<NamedRelatedResourceRep> vcenters;

    public VcenterList() {
    }

    public VcenterList(List<NamedRelatedResourceRep> vcenters) {
        this.vcenters = vcenters;
    }

    /**
     * List of vCenter objects that exists in ViPR.
     * 
     */
    @XmlElement(name = "vcenter")
    public List<NamedRelatedResourceRep> getVcenters() {
        if (vcenters == null) {
            vcenters = new ArrayList<NamedRelatedResourceRep>();
        }
        return vcenters;
    }

    public void setVcenters(List<NamedRelatedResourceRep> vcenters) {
        this.vcenters = vcenters;
    }
}
