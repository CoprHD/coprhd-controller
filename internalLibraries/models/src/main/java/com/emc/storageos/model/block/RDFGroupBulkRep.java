/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_rdfgroups")
public class RDFGroupBulkRep extends BulkRestRep {
    private List<RDFGroupRestRep> rdfGroups;

    @XmlElement(name = "rdfGroup")
    public List<RDFGroupRestRep> getRDFGroups() {
        if (rdfGroups == null) {
            rdfGroups = new ArrayList<RDFGroupRestRep>();
        }
        return rdfGroups;
    }

    public void setRDFGroups(List<RDFGroupRestRep> rdfGroups) {
        this.rdfGroups = rdfGroups;
    }

    public RDFGroupBulkRep() {
    }

    public RDFGroupBulkRep(List<RDFGroupRestRep> rdfGroups) {
        this.rdfGroups = rdfGroups;
    }
}
