/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderList;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

import java.net.URI;
import java.util.List;

/**
 * Authentication Providers resources.
 * <p>
 * Base URL: <tt>/vdc/admin/authnproviders</tt>
 * 
 * @see AuthnProviderRestRep
 */
public class AuthnProviders extends AbstractCoreResources<AuthnProviderRestRep> implements
        TopLevelResources<AuthnProviderRestRep> {
    public AuthnProviders(ViPRCoreClient parent, RestClient client) {
        super(parent, client, AuthnProviderRestRep.class, PathConstants.AUTHN_PROVIDER_URL);
    }

    @Override
    public AuthnProviders withInactive(boolean inactive) {
        return (AuthnProviders) super.withInactive(inactive);
    }

    @Override
    public AuthnProviders withInternal(boolean internal) {
        return (AuthnProviders) super.withInternal(internal);
    }

    /**
     * Lists all authentication providers.
     * <p>
     * API Call: <tt>GET /vdc/admin/authnproviders</tt>
     * 
     * @return the list of authentication providers.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        AuthnProviderList response = client.get(AuthnProviderList.class, baseUrl);
        return ResourceUtils.defaultList(response.getProviders());
    }

    @Override
    public List<AuthnProviderRestRep> getAll() {
        return getAll(null);
    }

    @Override
    public List<AuthnProviderRestRep> getAll(ResourceFilter<AuthnProviderRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs);
    }

    /**
     * Creates an authentication provider.
     * <p>
     * API Call: <tt>POST /vdc/admin/authnproviders</tt>
     * 
     * @param input
     *        the create configuration.
     * @return the created authentication provider.
     */
    public AuthnProviderRestRep create(AuthnCreateParam input) {
        return client.post(AuthnProviderRestRep.class, input, baseUrl);
    }

    /**
     * Updates an authentication provider.
     * <p>
     * API Call: <tt>PUT /vdc/admin/authnproviders/{id}</tt>
     * 
     * @param id
     *        the authentication provider ID.
     * @param input
     *        the update configuration.
     * @return the updated authentication provider.
     */
    public AuthnProviderRestRep update(URI id, AuthnUpdateParam input) {
        return client.put(AuthnProviderRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Updates an authentication provider with allow_group_attr_change set to true.
     * <p>
     * API Call: <tt>PUT /vdc/admin/authnproviders/{id}?allow_group_attr_change=true</tt>
     *
     * @param id
     *        the authentication provider ID.
     * @param input
     *        the update configuration.
     * @return the updated authentication provider.
     */
    public AuthnProviderRestRep forceUpdate(URI id, AuthnUpdateParam input) {
        URI forceUpdateUri = client.uriBuilder(getIdUrl()).queryParam("allow_group_attr_change", true).build(id);
        return client.putURI(AuthnProviderRestRep.class, input, forceUpdateUri);
    }

    /**
     * Deletes an authentication provider.
     * <p>
     * API Call: <tt>DELETE /vdc/admin/authnproviders/{id}</tt>
     * 
     * @param id
     *        the authentication provider ID.
     */
    public void delete(URI id) {
        client.delete(String.class, getIdUrl(), id);
    }
}
