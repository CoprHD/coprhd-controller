/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * Common class to all tenant resource types
 * 
 * @author elalih
 */
@XmlType(name = "abstractComputeSystemRestRep")
public abstract class ComputeSystemRestRep extends DiscoveredSystemObjectRestRep {
    private RelatedResourceRep tenant;

    public ComputeSystemRestRep() {
    }

    public ComputeSystemRestRep(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    /**
     * The tenant organization of the host.
     * 
     * @return the tenant organization of the host.
     */
    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }
}
