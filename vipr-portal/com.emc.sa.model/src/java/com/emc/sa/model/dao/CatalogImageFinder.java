/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.model.dao;

import com.emc.storageos.db.client.model.uimodels.CatalogImage;

public class CatalogImageFinder extends TenantModelFinder<CatalogImage> {
    public CatalogImageFinder(DBClientWrapper client) {
        super(CatalogImage.class, client);
    }
}
