/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.datatable;

import java.net.URI;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.vipr.client.core.util.ResourceUtils;

public class NamedResourceDataTable extends DataTable {
    public NamedResourceDataTable() {
        addColumn("name").setSearchable(true).setSortable(true);
    }

    public static class NamedResource {
        public String id;
        public String name;

        public NamedResource() {
        }

        public NamedResource(URI id, String name) {
            this.id = id.toString();
            this.name = name;
        }

        public NamedResource(NamedRelatedResourceRep value) {
            id = ResourceUtils.stringId(value);
            name = ResourceUtils.name(value);
        }

        public NamedResource(DataObjectRestRep value) {
            id = ResourceUtils.stringId(value);
            name = ResourceUtils.name(value);
        }
    }
}
