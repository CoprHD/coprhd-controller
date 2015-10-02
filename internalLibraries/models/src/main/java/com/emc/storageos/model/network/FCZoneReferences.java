/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * This is an internal structure used only for debugging. It keeps track of which
 * Export Groups (and volumes) are using a fiber channel zone.
 */
@XmlRootElement(name = "fc_zone_references")
public class FCZoneReferences {

    private List<FCZoneReferenceRestRep> references;

    public FCZoneReferences() {
    }

    public FCZoneReferences(List<FCZoneReferenceRestRep> references) {
        this.references = references;
    }

    /**
     * List of FC (Fibre Channel) Zone References that keeps
     * track of which export groups and volumes are using a fibre channel zone.
     * 
     */
    @XmlElement(name = "fc_zone_reference")
    public List<FCZoneReferenceRestRep> getReferences() {
        if (references == null) {
            references = new ArrayList<FCZoneReferenceRestRep>();
        }
        return references;
    }

    public void setReferences(List<FCZoneReferenceRestRep> references) {
        this.references = references;
    }

}
