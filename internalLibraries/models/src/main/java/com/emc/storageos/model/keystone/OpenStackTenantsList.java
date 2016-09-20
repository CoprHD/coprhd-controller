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
package com.emc.storageos.model.keystone;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "openstack_tenants")
public class OpenStackTenantsList {
    private List<NamedRelatedResourceRep> tenants;

    public OpenStackTenantsList() {
    }

    public OpenStackTenantsList(List<NamedRelatedResourceRep> tenants) {
        this.tenants = tenants;
    }

    /**
     * List of OpenStack tenants.
     *
     */
    @XmlElement(name = "openstack_tenant")
    public List<NamedRelatedResourceRep> getTenants() {
        if (tenants == null) {
            tenants = new ArrayList<NamedRelatedResourceRep>();
        }
        return tenants;
    }

    public void setTenants(List<NamedRelatedResourceRep> tenants) {
        this.tenants = tenants;
    }
}
