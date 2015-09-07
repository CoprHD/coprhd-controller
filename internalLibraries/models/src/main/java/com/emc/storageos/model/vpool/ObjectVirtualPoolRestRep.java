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

    public ObjectVirtualPoolRestRep() {
    }
    
    @XmlElement(name = "max_retention")
    public Integer getMaxRetention() {
        return maxRetention;
    }

    public void setMaxRetention(Integer maxRetention) {
        this.maxRetention = maxRetention;
    }
}
