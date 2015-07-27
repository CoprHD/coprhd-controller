/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

public interface TenantResource {

    /**
     * The tenant organization owner of this resource.
     * 
     * @return the tenant organization owner of this resource
     */
    @RelationIndex(cf = "RelationIndex", type = TenantOrg.class)
    @Name("tenant")
    public URI getTenant();

    /**
     * Sets the tenant organization owner of this resource.
     * 
     * @param tenant the tenant organization URI
     */
    public void setTenant(URI tenant);

    /**
     * Returns the list of parameters used in audit logs for this resource.
     * @return the list of parameters used in audit logs for this resource.
     */
    public Object[] auditParameters();
    
    /**
     * Return the data object instance that backs this interface
     * 
     * @return the data object instance that backs this interface
     */
    public <T extends DataObject> DataObject findDataObject();
}