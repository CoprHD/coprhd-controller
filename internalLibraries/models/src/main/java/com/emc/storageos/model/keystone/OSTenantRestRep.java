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

import com.emc.storageos.model.DataObjectRestRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "coprhd_os_tenant")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class OSTenantRestRep extends DataObjectRestRep {

    private Boolean enabled;
    private Boolean excluded;
    private String description;
    private String osId;

    /**
     * Indicates if Coprhd representation of OpenStack tenant is enabled.
     */
    @XmlElement(name = "enabled")
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Indicates if Coprhd representation of OpenStack tenant is excluded.
     */
    @XmlElement(name = "excluded")
    public Boolean getExcluded() {
        return excluded;
    }

    public void setExcluded(Boolean excluded) {
        this.excluded = excluded;
    }

    /**
     * Description of Coprhd representation of OpenStack tenant.
     */
    @XmlElement(name = "description")
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
    public String getOsId() {
        return osId;
    }

    public void setOsId(String osId) {
        this.osId = osId;
    }
}
