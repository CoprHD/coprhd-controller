/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.catalog.impl.SearchConstants.TENANT_ID_PARAM;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.CatalogPreferencesRestRep;
import com.emc.vipr.model.catalog.CatalogPreferencesUpdateParam;

public class CatalogPreferences {

    protected final ViPRCatalogClient2 parent;
    protected final RestClient client;
    
    public CatalogPreferences(ViPRCatalogClient2 parent, RestClient client) {
        this.parent = parent;
        this.client = client;
    }
    
    public CatalogPreferencesRestRep getPreferences() {
        return getPreferences(null);
    }
    
    public CatalogPreferencesRestRep getPreferences(URI tenantId) {
        UriBuilder builder = client.uriBuilder(PathConstants.CATALOG_PREFERENCES);
        if (tenantId != null) {
            builder.queryParam(TENANT_ID_PARAM, tenantId);
        }        
        return client.getURI(CatalogPreferencesRestRep.class, builder.build());
    }
    
    /**
     * Updates the catalog preferences
     * <p>
     * API Call: <tt>PUT /catalog/preferences</tt>
     * 
     * @param input
     *        the update preferences.
     * @return the updated catalog preferences
     */
    public CatalogPreferencesRestRep updatePreferences(CatalogPreferencesUpdateParam input) {
        UriBuilder builder = client.uriBuilder(PathConstants.CATALOG_PREFERENCES);
        return client.putURI(CatalogPreferencesRestRep.class, input, builder.build());
    }    

}
