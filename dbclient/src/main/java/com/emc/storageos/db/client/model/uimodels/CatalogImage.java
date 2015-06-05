/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;

/**
 * Storas an image to use in the catalog.
 * 
 * @author jonnymiller
 */
@Cf("CatalogImage")
public class CatalogImage extends ModelObject implements TenantDataObject {
    public static final String CONTENT_TYPE = "contentType";
    public static final String DATA = "data";

    private String tenant;
    private String contentType;
    private byte[] data;

    @Override
    @AlternateId("TenantToCatalogImage")
    @Name(TenantDataObject.TENANT_COLUMN_NAME)
    public String getTenant() {
        return tenant;
    }

    @Override
    public void setTenant(String tenant) {
        this.tenant = tenant;
        setChanged(TenantDataObject.TENANT_COLUMN_NAME);
    }

    @Name(CONTENT_TYPE)
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
        setChanged(CONTENT_TYPE);
    }

    @Name(DATA)
    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        setChanged(DATA);
    }

    @Override
    public String toString() {
        return getLabel();
    }
    
    @Override
    public Object[] auditParameters() {
        return new Object[] {getLabel(), 
                getContentType(), getTenant(), getId() };
    }        
}
