/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Parameter to Object VirtualPool update.
 */
@XmlRootElement(name = "object_vpool_update")
public class ObjectVirtualPoolUpdateParam extends VirtualPoolUpdateParam {

    private Integer maxRetention;

	public ObjectVirtualPoolUpdateParam() {
    }

    /**
     * The maximum retention settings for the virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "max_retention")
    public Integer getMaxRetention() {
        return maxRetention;
    }

    public void setMaxRetention(Integer maxRetention) {
        this.maxRetention = maxRetention;
    }
}
