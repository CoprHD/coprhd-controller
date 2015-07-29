/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.model.uimodels.CatalogImage;
import com.emc.sa.model.dao.ModelClient;

@Component
public class CatalogImageManagerImpl implements CatalogImageManager {

    private static final Logger log = Logger.getLogger(CatalogImageManagerImpl.class);

    @Autowired
    private ModelClient client;

    public CatalogImage getCatalogImageById(URI id) {
        if (id == null) {
            return null;
        }

        CatalogImage catalogImage = client.catalogImages().findById(id);

        return catalogImage;
    }

    public void createCatalogImage(CatalogImage catalogImage) {
        client.save(catalogImage);
    }

    public void updateCatalogImage(CatalogImage catalogImage) {
        client.save(catalogImage);
    }

    public void deleteCatalogImage(CatalogImage catalogImage) {
        client.delete(catalogImage);
    }

    public List<CatalogImage> getCatalogImages(URI tenantId) {
        return client.catalogImages().findAll(tenantId.toString());
    }

}
