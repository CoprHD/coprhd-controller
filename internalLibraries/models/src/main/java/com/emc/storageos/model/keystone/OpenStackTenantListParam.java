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

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "openstack_tenant_list_param")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class OpenStackTenantListParam {

    private List<OpenStackTenantParam> openstackTenants;

    /**
     * List of OpenStack Tenants.
     */
    @XmlElement(name = "openstack_tenants")
    @JsonProperty("openstack_tenants")
    public List<OpenStackTenantParam> getOpenstackTenants() {
        return openstackTenants;
    }

    public void setOpenstackTenants(List<OpenStackTenantParam> openstackTenants) {
        this.openstackTenants = openstackTenants;
    }
}
