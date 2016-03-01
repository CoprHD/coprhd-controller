/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Virtual pool of Object System type.
 * 
 */
@XmlRootElement(name = "object_vpool")
public class ObjectVirtualPoolRestRep extends VirtualPoolCommonRestRep {

	private Integer maxRetention;
    private Integer minDataCenters;

    public ObjectVirtualPoolRestRep() {
    }
    
    @XmlElement(name = "max_retention")
    public Integer getMaxRetention() {
        return maxRetention;
    }

    public void setMaxRetention(Integer maxRetention) {
        this.maxRetention = maxRetention;
    }
    
    /**
     * The minimum number of data centers required for each CoprHD storage pool
     * 
     */
    @XmlElement(name = "min_datacenters")
    public Integer getMinDataCenters() {
        return minDataCenters;
    }

    public void setMinDataCenters(Integer minDataCenters) {
        this.minDataCenters = minDataCenters;
    }    
}
