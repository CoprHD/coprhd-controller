/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

public class SimpleHostDataTable extends DataTable {
    public SimpleHostDataTable() {
        addColumn("name");
        addColumn("hostName");
        addColumn("type").setRenderFunction("render.operatingSystem");
        sortAll();
        setDefaultSort("name", "asc");
    }
}
