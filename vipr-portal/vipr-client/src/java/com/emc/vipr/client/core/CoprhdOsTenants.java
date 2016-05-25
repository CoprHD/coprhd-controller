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
import com.emc.storageos.model.keystone.*;
import com.emc.vipr.client.Task;
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
public class CoprhdOsTenants extends AbstractCoreResources<CoprhdOsTenant> implements
        TopLevelResources<CoprhdOsTenant> {
    public CoprhdOsTenants(ViPRCoreClient parent, RestClient client) {
        super(parent, client, CoprhdOsTenant.class, PathConstants.KEYSTONE_URL + "/ostenants");
    }

    @Override
    public CoprhdOsTenants withInactive(boolean inactive) {
        return (CoprhdOsTenants) super.withInactive(inactive);
    }

    @Override
    public CoprhdOsTenants withInternal(boolean internal) {
        return (CoprhdOsTenants) super.withInternal(internal);
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
        CoprhdOsTenantsList response = client.get(CoprhdOsTenantsList.class, baseUrl);
        return ResourceUtils.defaultList(response.getTenants());
    }

    /**
     * Gets all OpenStack tenants.
     * 
     * @return the list of all OpenStack tenants.
     */
    @Override
    public List<CoprhdOsTenant> getAll() {
        return getAll(null);
    }

    /**
     *
     */
    @Override
    public List<CoprhdOsTenant> getAll(ResourceFilter<CoprhdOsTenant> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs, filter);
    }

    /**
     * Gets OpenStack tenant with given ID.
     *
     * @return single OpenStack tenant.
     */
    public OpenStackTenantParam get(String id) {
        return client.get(OpenStackTenantParam.class, getIdUrl(), id);
    }

    /**
     *
     */
    /*public Task<CoprhdOsTenant> update(ComputeImageUpdate input) {
        return putTask(input, getIdUrl(), id);
    }*/
}