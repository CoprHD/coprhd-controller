/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getCatalogClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.CatalogCategoryCreateParam;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogCategoryUpdateParam;

import controllers.util.Models;

public class CatalogCategoryUtils {

    public static CatalogCategoryRestRep getRootCategory(URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().getRootCatalogCategory(tenantId);
    }

    public static CatalogCategoryRestRep getCatalogCategory(URI catalogCategoryId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().get(catalogCategoryId);
    }

    public static CatalogCategoryRestRep getRootCategory() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().getRootCatalogCategory(Models.currentAdminTenant());
    }

    public static List<CatalogCategoryRestRep> getCatalogCategories(CatalogCategoryRestRep category) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().getSubCategories(category.getId());
    }

    public static void upgradeCatalog() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.categories().upgradeCatalog(uri(Models.currentAdminTenant()));
    }

    public static void deleteCatalogCategory(URI catalogCategoryId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.categories().deactivate(catalogCategoryId);
    }

    public static void resetCatalogCategory(URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.categories().resetCatalog(tenantId);
    }

    public static CatalogCategoryRestRep createCatalogCategory(CatalogCategoryCreateParam createParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().create(createParam);
    }

    public static CatalogCategoryRestRep updateCatalogCategory(URI id, CatalogCategoryUpdateParam updateParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().update(id, updateParam);
    }

    public static List<ACLEntry> getACLs(String id) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().getACLs(uri(id));
    }

    public static List<ACLEntry> updateACLs(String id, ACLAssignmentChanges changes) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().updateACLs(uri(id), changes);
    }

    public static void moveUpCategory(URI catalogCategoryId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.categories().moveUp(catalogCategoryId);
    }

    public static void moveDownCategory(URI catalogCategoryId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.categories().moveDown(catalogCategoryId);
    }

    public static boolean isUpdateAvailable(URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.categories().upgradeAvailable(tenantId);
    }

}
