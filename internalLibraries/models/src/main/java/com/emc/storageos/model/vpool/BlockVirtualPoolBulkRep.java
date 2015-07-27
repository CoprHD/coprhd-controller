/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
     * @valid none
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
