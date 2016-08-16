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

import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DataObjectRestRep;
import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "openstack_tenant_param")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class OpenStackTenantParam extends DataObjectRestRep {
    private Boolean enabled;
    private Boolean excluded;
    private String description;
    private String osId;

    public OpenStackTenantParam() {
    }

    /**
     * Indicates if OpenStack tenant is enabled.
     */
    @XmlElement(name = "enabled")
    @JsonProperty("enabled")
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Indicates if OpenStack tenant is excluded.
     */
    @XmlElement(name = "excluded")
    @JsonProperty("excluded")
    public Boolean getExcluded() {
        return excluded;
    }

    public void setExcluded(Boolean excluded) {
        this.excluded = excluded;
    }

    /**
     * Description of OpenStack tenant.
     */
    @XmlElement(name = "description")
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * ID of OpenStack tenant.
     */
    @XmlElement(name = "os_id")
    @JsonProperty("os_id")
    public String getOsId() {
        return osId;
    }

    public void setOsId(String osId) {
        this.osId = osId;
    }
}
