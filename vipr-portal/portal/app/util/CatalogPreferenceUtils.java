/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getCatalogClient;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.CatalogPreferencesRestRep;
import com.emc.vipr.model.catalog.CatalogPreferencesUpdateParam;

public class CatalogPreferenceUtils {

    public static CatalogPreferencesRestRep getCatalogPreferences() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.catalogPreferences().getPreferences();
    }

    public static CatalogPreferencesRestRep getCatalogPreferences(String tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.catalogPreferences().getPreferences(uri(tenantId));
    }

    public static CatalogPreferencesRestRep updatePreferences(CatalogPreferencesUpdateParam updateParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.catalogPreferences().updatePreferences(updateParam);
    }

}
