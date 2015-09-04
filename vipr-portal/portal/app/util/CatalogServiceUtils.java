/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getCatalogClient;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.emc.vipr.model.catalog.CatalogCategoryRestRep;
import com.emc.vipr.model.catalog.CatalogServiceCreateParam;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.CatalogServiceUpdateParam;

public class CatalogServiceUtils {

    public static CatalogServiceRestRep getCatalogService(RelatedResourceRep resource) {
        if (resource != null) {
            return getCatalogService(resource.getId());
        }
        return null;
    }

    public static CatalogServiceRestRep getCatalogService(URI id) {
        ViPRCatalogClient2 catalog = getCatalogClient();

        CatalogServiceRestRep service = null;
        try {
            service = catalog.services().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                service = null;
            }
            else {
                throw e;
            }
        }
        return service;
    }

    public static List<CatalogServiceRestRep> getCatalogServices(CatalogCategoryRestRep category) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.services().findByCatalogCategory(category.getId());
    }

    public static List<CatalogServiceRestRep> getRecentServices() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.services().getRecentServices();
    }

    public static void deleteCatalogService(URI catalogServiceId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.services().deactivate(catalogServiceId);
    }

    public static CatalogServiceRestRep createCatalogService(CatalogServiceCreateParam createParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.services().create(createParam);
    }

    public static CatalogServiceRestRep updateCatalogService(URI id, CatalogServiceUpdateParam updateParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.services().update(id, updateParam);
    }

    public static List<ACLEntry> getACLs(String id) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.services().getACLs(uri(id));
    }

    public static List<ACLEntry> updateACLs(String id, ACLAssignmentChanges changes) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.services().updateACLs(uri(id), changes);
    }

    public static void moveUpService(URI catalogServiceId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.services().moveUp(catalogServiceId);
    }

    public static void moveDownService(URI catalogServiceId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.services().moveDown(catalogServiceId);
    }

    public static void moveUpServiceField(URI catalogServiceId, String fieldName) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.services().moveUpField(catalogServiceId, fieldName);
    }

    public static void moveDownServiceField(URI catalogServiceId, String fieldName) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.services().moveDownField(catalogServiceId, fieldName);
    }

}
