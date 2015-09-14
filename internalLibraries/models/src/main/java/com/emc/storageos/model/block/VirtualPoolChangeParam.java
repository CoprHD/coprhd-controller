/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam;

/**
 * Specifies the parameters to change the virtual pool for a volume.
 */
@Deprecated
@XmlRootElement(name = "volume_vpool_change")
public class VirtualPoolChangeParam {

    private URI virtualPool;
    private BlockVirtualPoolProtectionParam protection;
    private URI consistencyGroup;

    public VirtualPoolChangeParam() {
    }

    public VirtualPoolChangeParam(URI virtualPool,
            BlockVirtualPoolProtectionParam protection) {
        this.virtualPool = virtualPool;
        this.protection = protection;
    }

    /**
     * ID of the new virtual pool.
     * 
     * @valid example: a valid URI
     */
    @XmlElement(required = true, name = "vpool")
    @JsonProperty("vpool")
    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
    }

    /**
     * Virtual Pool (Mirror or RecoverPoint) protection
     * parameters.
     * 
     * @valid none
     */
    @XmlElement(required = false, name = "protection")
    public BlockVirtualPoolProtectionParam getProtection() {
        return protection;
    }

    public void setProtection(BlockVirtualPoolProtectionParam protection) {
        this.protection = protection;
    }

    /**
     * The ViPR consistency group to associate the volume with for
     * the creation of the RecoverPoint consistency group.
     * 
     * @return The Consistency Group to associate the volume during creation of RP consistency group
     */
    @XmlElement(name = "consistency_group")
    public URI getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(URI consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }

}
