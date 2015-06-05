/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;

import java.util.List;

public class CatalogServiceAndFields {
    
    private CatalogService catalogService;
    private List<CatalogServiceField> catalogServiceFields;
    public CatalogService getCatalogService() {
        return catalogService;
    }
    public void setCatalogService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }
    public List<CatalogServiceField> getCatalogServiceFields() {
        return catalogServiceFields;
    }
    public void setCatalogServiceFields(
            List<CatalogServiceField> catalogServiceFields) {
        this.catalogServiceFields = catalogServiceFields;
    }
    
    

}
