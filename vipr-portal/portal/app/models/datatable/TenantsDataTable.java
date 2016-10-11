/*
 * Copyright (c) 2015 EMC Corporation
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

import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.google.common.collect.Lists;
import models.TenantSource;
import org.apache.commons.lang.StringUtils;
import util.datatable.DataTable;
import util.datatable.DataTableColumnConfiguration;

import java.util.List;

public class TenantsDataTable extends DataTable {

    public TenantsDataTable() {
        DataTableColumnConfiguration nameColumn = addColumn("name");

        nameColumn.setRenderFunction("render.editableLink");
        addColumn("description");
        addColumn("mappedDomains");
        addColumn("source");

        sortAll();
        setDefaultSortField("name");
    }

    public static class Tenant {
        public String id;
        public String name;
        public String description;
        public long quota;
        public String tags;
        public String mappedDomains;
        public boolean editable;
        public String source;

        public Tenant(TenantOrgRestRep tenant, boolean editable) {
            id = tenant.getId().toString();
            name = tenant.getName();
            description = tenant.getDescription();
            this.editable = editable;

            List<String> domains = Lists.newArrayList();
            List<UserMappingParam> userMappings = tenant.getUserMappings();

            for (UserMappingParam userMapping : userMappings) {
                domains.add(userMapping.getDomain());
            }

            mappedDomains = StringUtils.join(domains, ", ");
            tags = StringUtils.join(tenant.getTags(), ", ");

            source = TenantSource.getTenantSource(userMappings);
        }
    }
}
