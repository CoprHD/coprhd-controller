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

package com.emc.storageos.keystone.restapi.model.response;

/**
 * Keystone API Tenant response class.
 */
public class TenantResponse {

    private TenantV2 tenants[];
    private String tenants_links[];

    public TenantV2[] getTenants() {
        return tenants;
    }

    public void setTenants(TenantV2[] tenants) {
        this.tenants = tenants;
    }

    public String[] getTenants_links() {
        return tenants_links;
    }

    public void setTenants_links(String[] tenants_links) {
        this.tenants_links = tenants_links;
    }
}
