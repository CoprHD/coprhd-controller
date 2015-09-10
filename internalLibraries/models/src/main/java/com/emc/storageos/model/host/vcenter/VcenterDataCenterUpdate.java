/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Request PUT parameter for vCenter data center update operation.
 */
@XmlRootElement(name = "vcenter_data_center_update")
public class VcenterDataCenterUpdate extends VcenterDataCenterParam {
    private URI tenantId;

    public VcenterDataCenterUpdate() {
    }

    public VcenterDataCenterUpdate(String name) {
        super(name);
    }

    public VcenterDataCenterUpdate(String name, URI tenantId) {
        super(name);
        this.tenantId = tenantId;
    }

    /**
     * The tenant URI for the vCenterDataCenter.
     *
     * @valid none
     */
    @XmlElement(name = "tenant")
    public URI getTenant() {
        return tenantId;
    }

    public void setTenant(URI tenantId) {
        this.tenantId = tenantId;
    }

}
