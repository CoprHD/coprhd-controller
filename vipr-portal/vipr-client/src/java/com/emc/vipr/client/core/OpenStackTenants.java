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
        super(parent, client, OpenStackTenantParam.class, PathConstants.KEYSTONE_URL);
    }

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
        OpenStackTenantsList response = client.get(OpenStackTenantsList.class, baseUrl + "/tenants");
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
     *
     */
    @Override
    public List<OpenStackTenantParam> getAll(ResourceFilter<OpenStackTenantParam> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    public void registerOpenStackTenants(OpenStackTenantListParam list) {
        OpenStackTenantListParam response = client.post(OpenStackTenantListParam.class, list, baseUrl + "/tenants");
        //return defaultList(response.getOpenstack_tenants());
    }
}