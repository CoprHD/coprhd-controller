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
package com.emc.storageos.db.client.model;


/**
 * OpenStack Tenant data object.
 */

@Cf("OSTenant")
public class OSTenant extends DataObject{

    private String osId;
    private String description;
    private Boolean enabled;
    private String name;
    private Boolean excluded;

    @Name("osid")
    public String getOsId() {
        return osId;
    }

    public void setOsId(String osId) {
        this.osId = osId;
        this.setChanged("osid");
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.setChanged("description");
    }

    @Name("enabled")
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        this.setChanged("enabled");
    }

    @Name("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.setChanged("name");
    }

    @Name("excluded")
    public Boolean getExcluded() {
        return excluded;
    }

    public void setExcluded(Boolean excluded) {
        this.excluded = excluded;
        this.setChanged("excluded");
    }
}
