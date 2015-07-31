/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * An instance of data center in a {@link Vcenter}
 * 
 * @author elalih
 * 
 */
@Cf("VcenterDataCenter")
public class VcenterDataCenter extends AbstractTenantResource {
    private URI _vcenter;
    private String _externalId;

    /**
     * Gets the VcenterDataCenter parent vcenter.
     * 
     * @return the VcenterDataCenter parent vcenter.
     */
    @RelationIndex(cf = "RelationIndex", type = Vcenter.class)
    @Name("vcenter")
    public URI getVcenter() {
        return _vcenter;
    }

    /**
     * Sets the VcenterDataCenter vcenter server
     * 
     * @param vcenter the vcenter URI
     */
    public void setVcenter(URI vcenter) {
        this._vcenter = vcenter;
        setChanged("vcenter");
    }

    /**
     * Returns the list of parameters used in audit logs for this data center.
     * 
     * @return the list of parameters used in audit logs for this data center.
     */
    public Object[] auditParameters() {
        return new Object[] { getLabel(), getVcenter(), getTenant(), getId() };
    }

    /**
     * ID of this datacenter on an external system such as vCenter.
     * 
     * @return
     */
    @AlternateId("AltIdIndex")
    @Name("externalId")
    public String getExternalId() {
        return _externalId;
    }

    public void setExternalId(String externalId) {
        this._externalId = externalId;
        setChanged("externalId");
    }
}
