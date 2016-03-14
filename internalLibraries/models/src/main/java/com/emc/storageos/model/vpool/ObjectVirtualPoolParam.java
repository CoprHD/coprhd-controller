/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter to Object VirtualPool creation
 */
@XmlRootElement(name = "object_vpool_create")
public class ObjectVirtualPoolParam extends VirtualPoolCommonParam {

	private Integer maxRetention;
    private Integer minDataCenters;

    public ObjectVirtualPoolParam() {
    }
    
    /**
     * The maximum retention settings for the virtual pool.
     * 
     */
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
