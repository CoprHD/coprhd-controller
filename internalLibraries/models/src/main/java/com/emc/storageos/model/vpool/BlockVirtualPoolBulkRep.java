/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import com.emc.storageos.model.BulkRestRep;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_block_vpools")
public class BlockVirtualPoolBulkRep extends BulkRestRep {
    private List<BlockVirtualPoolRestRep> virtualPools;

    /**
     * List of virtual pools.
     * 
     */
    @XmlElement(name = "block_vpool")
    @JsonProperty("block_vpool")
    public List<BlockVirtualPoolRestRep> getVirtualPools() {
        if (virtualPools == null) {
            virtualPools = new ArrayList<BlockVirtualPoolRestRep>();
        }
        return virtualPools;
    }

    public void setVirtualPools(List<BlockVirtualPoolRestRep> virtualPools) {
        this.virtualPools = virtualPools;
    }

    public BlockVirtualPoolBulkRep() {
    }

    public BlockVirtualPoolBulkRep(List<BlockVirtualPoolRestRep> virtualPools) {
        this.virtualPools = virtualPools;
    }
}
