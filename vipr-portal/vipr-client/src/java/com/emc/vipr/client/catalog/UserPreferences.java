/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.catalog.impl.SearchConstants.USER_NAME_PARAM;

import javax.ws.rs.core.UriBuilder;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.UserPreferencesRestRep;
import com.emc.vipr.model.catalog.UserPreferencesUpdateParam;

public class UserPreferences {

    protected final ViPRCatalogClient2 parent;
    protected final RestClient client;

    public UserPreferences(ViPRCatalogClient2 parent, RestClient client) {
        this.parent = parent;
        this.client = client;
    }

    public UserPreferencesRestRep getPreferences() {
        return getPreferences(null);
    }

    public UserPreferencesRestRep getPreferences(String username) {
        UriBuilder builder = client.uriBuilder(PathConstants.USER_PREFERENCES);
        if (username != null) {
            builder.queryParam(USER_NAME_PARAM, username);
        }
        return client.getURI(UserPreferencesRestRep.class, builder.build());
    }

    /**
     * Updates the user preferences
     * <p>
     * API Call: <tt>PUT /user/preferences</tt>
     * 
     * @param input
     *            the update preferences.
     * @return the updated user preferences
     */
    public UserPreferencesRestRep updatePreferences(UserPreferencesUpdateParam input) {
        UriBuilder builder = client.uriBuilder(PathConstants.USER_PREFERENCES);
        return client.putURI(UserPreferencesRestRep.class, input, builder.build());
    }

}
