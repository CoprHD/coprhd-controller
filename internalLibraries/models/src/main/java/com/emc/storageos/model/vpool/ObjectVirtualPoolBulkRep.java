/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import com.emc.storageos.model.BulkRestRep;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * List of all object virtual pools, returned as a response
 * to a REST request.
 * 
 */
@XmlRootElement(name = "bulk_object_vpools")
public class ObjectVirtualPoolBulkRep extends BulkRestRep {
    private List<ObjectVirtualPoolRestRep> virtualPools;

    /**
     * List of all virtual pools of object System type.
     * 
     */
    @XmlElement(name = "object_vpool")
    @JsonProperty("object_vpool")
    public List<ObjectVirtualPoolRestRep> getVirtualPools() {
        if (virtualPools == null) {
            virtualPools = new ArrayList<ObjectVirtualPoolRestRep>();
        }
        return virtualPools;
    }

    public void setVirtualPools(List<ObjectVirtualPoolRestRep> virtualPools) {
        this.virtualPools = virtualPools;
    }

    public ObjectVirtualPoolBulkRep() {
    }

    public ObjectVirtualPoolBulkRep(List<ObjectVirtualPoolRestRep> virtualPools) {
        this.virtualPools = virtualPools;
    }
}
