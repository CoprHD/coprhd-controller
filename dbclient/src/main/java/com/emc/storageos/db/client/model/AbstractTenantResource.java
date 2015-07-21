/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * A common class for resources the belong to a tenant organization
 * @author elalih
 *
 */
public abstract class AbstractTenantResource extends DataObject implements TenantResource {
    private URI _tenant;

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.TenantResource#getTenant()
     */
    @Override
    @RelationIndex(cf = "RelationIndex", type = TenantOrg.class)
    @Name("tenant")
    public URI getTenant() {
        return _tenant;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.TenantResource#setTenant(java.net.URI)
     */
    @Override
    public void setTenant(URI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.TenantResource#auditParameters()
     */
    @Override
    public abstract Object[] auditParameters();

    /* (non-Javadoc)
     * @see com.emc.storageos.db.client.model.TenantResource#getDataObject()
     */
    @Override
    public DataObject findDataObject() {
        return (DataObject) this;
    }
    
    
}
