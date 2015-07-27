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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Capacity response
 */
@XmlRootElement(name = "capacity")
public class CapacityResponse {

    private String freeGb;
    private String usedGb;
    private String provissionedGb;
    private String percentUsed;
    private String percentProvisioned;
    private String netFreeGb;
    
    public CapacityResponse() {}

    /**
     * The free storage capacity of the specified VirtualPool 
     * or VirtualArray. 
     * @valid none
     */     
    @XmlElement(name = "free_gb")
    public String getFreeGb() {
        return freeGb;
    }
	

    public void setFreeGb(String freeGb) {
        this.freeGb = freeGb;
    }

    /**
     * The net free storage capacity of the specified VirtualPool 
     * or VirtualArray.
     */
    @XmlElement(name = "net_free_gb")
    public String getNetFreeGb() {
        return netFreeGb;
    }

    public void setNetFreeGb(String netFreeGb) {
        this.netFreeGb = netFreeGb;
    }

    /**
     * The used storage capacity of the specified VirtualPool 
     * or VirtualArray. 
     * @valid none
     */     
    @XmlElement(name = "used_gb")
    public String getUsedGb() {
        return usedGb;
    }

    public void setUsedGb(String usedGb) {
        this.usedGb = usedGb;
    }

    /**
     * The subscribed storage capacity of the specified 
     * VirtualPool or VirtualArray. 
     * @valid none
     */     
    @XmlElement(name = "provisioned_gb")
    public String getProvissionedGb() {
        return provissionedGb;
    }

    public void setProvissionedGb(String provissionedGb) {
        this.provissionedGb = provissionedGb;
    }

    /**
     * The actual used percent of the usable capacity of the 
     * specified VirtualPool or VirtualArray. 
     * @valid none
     */     
    @XmlElement(name = "percent_used")
    public String getPercentUsed() {
        return percentUsed;
    }

    public void setPercentUsed(String percentUsed) {
        this.percentUsed = percentUsed;
    }

    /**
     * The provisioned percent of the usable capacity of the 
     * specified VirtualPool or VirtualArray. 
     * @valid none
     */     
    @XmlElement(name = "percent_provisioned")
    public String getPercentProvisioned() {
        return percentProvisioned;
    }

    public void setPercentProvisioned(String percentProvisioned) {
        this.percentProvisioned = percentProvisioned;
    }
    
}
