/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_tenants")
public class TenantOrgBulkRep extends BulkRestRep {
    private List<TenantOrgRestRep> tenants;

    /**
     * List of tenants
     * 
     * @valid none
     */
    @XmlElement(name = "tenant")
    public List<TenantOrgRestRep> getTenants() {
        if (tenants == null) {
            tenants = new ArrayList<TenantOrgRestRep>();
        }
        return tenants;
    }

    public void setTenants(List<TenantOrgRestRep> tenants) {
        this.tenants = tenants;
    }

    public TenantOrgBulkRep() {
    }

    public TenantOrgBulkRep(List<TenantOrgRestRep> tenants) {
        this.tenants = tenants;
    }
}
