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
package models.datatable;

import com.emc.storageos.model.keystone.OpenStackTenantParam;

import util.datatable.DataTable;


import com.emc.storageos.model.keystone.OpenStackTenantParam;

import util.datatable.DataTable;
import util.datatable.DataTableColumnConfiguration;

public class OpenStackTenantsDataTable extends DataTable {

    public OpenStackTenantsDataTable() {
        addColumn("name");
        addColumn("add");
        addColumn("exclude");
        addColumn("description");
        sortAll();
        setDefaultSortField("name");
    }

    public static class OpenStackTenant {
        public String id;
        public String name;
        public String description;
        public boolean enabled;
        public boolean exclude = false;

        public OpenStackTenant(OpenStackTenantParam tenant) {
            this.id = tenant.getOsId();
            this.name = tenant.getName();
            this.description = tenant.getDescription();
            this.enabled = tenant.getEnabled();
            this.exclude = tenant.getExcluded();
        }
    }
}
