/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RelatedResourceRep;

/**
 * Preferred pool parameter representing pool with its type
 */
@XmlRootElement(name = "preferred_pool")
public class PreferredPoolParam {

    private RelatedResourceRep poolRep;
    private String type;

    public PreferredPoolParam() {
    }

    public PreferredPoolParam(RelatedResourceRep pool, String type) {
        this.poolRep = pool;
        this.type = type;
    }

    /**
     * Preferred pool
     *
     */
    @XmlElement(name = "pool", required = true)
    public RelatedResourceRep getPoolRep() {
        return poolRep;
    }

    public void setPoolRep(RelatedResourceRep poolRep) {
        this.poolRep = poolRep;
    }

    /**
     * Export type
     *
     */
    @XmlElement(name = "type", required = true)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
