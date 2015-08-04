/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.CatalogCategory;

public interface CatalogCategoryManager {

    public void upgradeCatalog(URI tenantId) throws IOException;

    public CatalogCategory getOrCreateRootCategory(URI tenantId);

    public void restoreDefaultCatalog(URI tenant) throws IOException;

    public boolean isCatalogUpdateAvailable(URI tenantId);

    public CatalogCategory getCatalogCategoryById(URI id);

    public void createCatalogCategory(CatalogCategory catalogCategory);

    public void updateCatalogCategory(CatalogCategory catalogCategory);

    public void deleteCatalogCategory(CatalogCategory catalogCategory);

    public List<CatalogCategory> getSubCategories(URI parentCatalogCategoryId);

    public void moveUpCatalogCategory(URI catalogCategoryId);

    public void moveDownCatalogCategory(URI catalogCategoryId);

}
