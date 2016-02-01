/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import javax.xml.bind.annotation.XmlElement;

/**
 * Captures POST data for a vCenter data center.
 */
public class VcenterDataCenterParam {

    private String name;

    public VcenterDataCenterParam() {
    }

    public VcenterDataCenterParam(String name) {
        this.name = name;
    }

    /**
     * The name label for this vCenter data center
     * 
     */
    @XmlElement(required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
