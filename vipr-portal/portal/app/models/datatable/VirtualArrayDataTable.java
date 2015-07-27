/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;
import com.emc.storageos.model.varray.VirtualArrayRestRep;

public class VirtualArrayDataTable extends DataTable {

    public VirtualArrayDataTable() {
        addColumn("id").hidden();
        addColumn("name").setRenderFunction("renderLink");

        sortAllExcept("id");
        setDefaultSort("name", "asc");
    }

    public static class VirtualArrayInfo {
        public String id;
        public String name;

        public VirtualArrayInfo() {
        }

        public VirtualArrayInfo(VirtualArrayRestRep virtualArray) {
            this.id = virtualArray.getId().toString();
            this.name = virtualArray.getName();
        }
    }
}
