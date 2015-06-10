/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getCatalogClient;

import java.net.URI;
import java.util.List;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.CatalogImageCreateParam;
import com.emc.vipr.model.catalog.CatalogImageRestRep;
import com.emc.vipr.model.catalog.CatalogImageUpdateParam;

import controllers.util.Models;

public class CatalogImageUtils {

    public static List<CatalogImageRestRep> getCatalogImages() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.images().getByTenant(uri(Models.currentAdminTenant()));
    }
    
    public static CatalogImageRestRep getCatalogImage(URI id) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.images().get(id);
    }
    
    public static void deleteCatalogImage(URI id) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        catalog.images().deactivate(id);
    }
    
    public static CatalogImageRestRep createCatalogImage(CatalogImageCreateParam createParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.images().create(createParam);
    }
    
    public static CatalogImageRestRep updateCatalogImage(URI id, CatalogImageUpdateParam updateParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.images().update(id, updateParam);        
    }

}
