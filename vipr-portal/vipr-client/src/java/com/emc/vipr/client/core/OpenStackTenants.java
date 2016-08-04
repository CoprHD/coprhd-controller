/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.vipr.client.core;

import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.keystone.OSTenantListRestRep;
import com.emc.storageos.model.keystone.OpenStackTenantListParam;
import com.emc.storageos.model.keystone.OpenStackTenantParam;
import com.emc.storageos.model.keystone.OpenStackTenantsList;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * OpenStack tenants resources.
 * <p>
 * Base URL: <tt>"/v2/keystone"</tt>
 */
public class OpenStackTenants extends AbstractCoreResources<OpenStackTenantParam> implements
        TopLevelResources<OpenStackTenantParam> {
    public OpenStackTenants(ViPRCoreClient parent, RestClient client) {
        super(parent, client, OpenStackTenantParam.class, PathConstants.KEYSTONE_TENANTS_URL);
    }
    private final String OS_TENANTS_URL = PathConstants.KEYSTONE_OS_TENANTS_URL;

    @Override
    public OpenStackTenants withInactive(boolean inactive) {
        return (OpenStackTenants) super.withInactive(inactive);
    }

    @Override
    public OpenStackTenants withInternal(boolean internal) {
        return (OpenStackTenants) super.withInternal(internal);
    }

    /**
     * Lists all OpenStack tenants.
     * <p>
     * API Call: <tt>GET /v2/keystone/tenants</tt>
     * 
     * @return the list of all OpenStack tenants references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        OpenStackTenantsList response = client.get(OpenStackTenantsList.class, baseUrl);
        return ResourceUtils.defaultList(response.getTenants());
    }

    /**
     * Gets all OpenStack tenants.
     * 
     * @return the list of all OpenStack tenants.
     */
    @Override
    public List<OpenStackTenantParam> getAll() {
        return getAll(null);
    }

    /**
     * Lists all OpenStack tenants.
     * <p>
     * API Call: <tt>GET /v2/keystone/ostenants</tt>
     *
     * @return list of OpenStack tenants.
     */
    public OSTenantListRestRep getOpenStackTenants() {
        return client.get(OSTenantListRestRep.class, OS_TENANTS_URL);
    }

    /**
     * Updates OpenStack tenants.
     * <p>
     * API Call: <tt>PUT /v2/keystone/ostenants</tt>
     *
     * @param list list of OpenStack tenants to update.
     *
     * @return list of updated OpenStack tenants.
     */
    public OSTenantListRestRep updateOpenStackTenants(OSTenantListRestRep list) {
        return client.put(OSTenantListRestRep.class, list, OS_TENANTS_URL);
    }

    @Override
    public List<OpenStackTenantParam> getAll(ResourceFilter<OpenStackTenantParam> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Gets OpenStack tenant with given ID.
     * <p>
     * API Call: <tt>PUT /v2/keystone/tenants/{id}</tt>
     *
     * @param id the OpenStack tenant ID.
     *
     * @return single OpenStack tenant.
     */
    public OpenStackTenantParam get(String id) {
        return client.get(OpenStackTenantParam.class, getIdUrl(), id);
    }

    /**
     * Creates representation of OpenStack Tenants in CoprHD.
     * <p>
     * API Call: <tt>PUT /v2/keystone/tenants</tt>
     *
     * @return list of saved OpenStack tenants.
     */
    public OSTenantListRestRep registerOpenStackTenants(OpenStackTenantListParam list) {
        return client.post(OSTenantListRestRep.class, list, baseUrl);
    }

    public void synchronizeOpenStackTenants() {
        client.put(String.class, OS_TENANTS_URL + "/sync");
    }
}