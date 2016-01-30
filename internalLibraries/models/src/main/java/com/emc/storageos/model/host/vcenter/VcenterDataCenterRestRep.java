/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.host.TenantResourceRestRep;

/**
 * REST Response representing an vCenter data center.
 */
@XmlRootElement(name = "vcenter_data_center")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VcenterDataCenterRestRep extends TenantResourceRestRep {
    private RelatedResourceRep vcenter;

    public VcenterDataCenterRestRep() {
    }

    public VcenterDataCenterRestRep(RelatedResourceRep vcenter) {
        this.vcenter = vcenter;
    }

    /**
     * The vCenter URI where this data center exists.
     * 
     * @return the vCenter URI where this data center exists.
     */
    @XmlElement(name = "vcenter")
    public RelatedResourceRep getVcenter() {
        return vcenter;
    }

    public void setVcenter(RelatedResourceRep vcenter) {
        this.vcenter = vcenter;
    }
}
