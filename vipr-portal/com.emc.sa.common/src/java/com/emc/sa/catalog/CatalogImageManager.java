/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.CatalogImage;

public interface CatalogImageManager {

    public CatalogImage getCatalogImageById(URI id);

    public void createCatalogImage(CatalogImage catalogImage);

    public void updateCatalogImage(CatalogImage catalogImage);

    public void deleteCatalogImage(CatalogImage catalogImage);

    public List<CatalogImage> getCatalogImages(URI tenantId);
}
