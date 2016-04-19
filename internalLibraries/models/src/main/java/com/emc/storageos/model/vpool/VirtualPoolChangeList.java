/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Lists the potential virtual pool for a block volume VirtualPool change for
 * a specific volume. Specifies for each VirtualPool in the list whether
 * or not the VirtualPool change would be allowed and if not, why.
 */
@XmlRootElement(name = "vpool_change_list")
public class VirtualPoolChangeList {

    // A list of VirtualPool changes.
    private List<VirtualPoolChangeRep> virtualPools;

    public VirtualPoolChangeList() {
    }

    public VirtualPoolChangeList(List<VirtualPoolChangeRep> virtualPools) {
        this.virtualPools = virtualPools;
    }

    /**
     * The list of virtual pool change response instances.
     * 
     * 
     * @return The list of virtual pool change response instances.
     */
    @XmlElement(name = "vpool_change")
    @JsonProperty("vpool_change")
    public List<VirtualPoolChangeRep> getVirtualPools() {
        if (virtualPools == null) {
            virtualPools = new ArrayList<VirtualPoolChangeRep>();
        }
        return virtualPools;
    }

    public void setVirtualPools(List<VirtualPoolChangeRep> virtualPools) {
        this.virtualPools = virtualPools;
    }
}
