/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.datatable;

import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.model.tenant.UserMappingParam;
import com.google.common.collect.Lists;
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

        public Tenant(TenantOrgRestRep tenant, boolean editable) {
            id = tenant.getId().toString();
            name = tenant.getName();
            description = tenant.getDescription();
            this.editable = editable;

            List<String> domains = Lists.newArrayList();
            for (UserMappingParam userMapping : tenant.getUserMappings()) {
                domains.add(userMapping.getDomain());
            }

            mappedDomains = StringUtils.join(domains, ", ");
            tags = StringUtils.join(tenant.getTags(), ", ");
        }
    }
}
