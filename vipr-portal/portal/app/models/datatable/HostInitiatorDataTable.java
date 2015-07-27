/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

public class HostInitiatorDataTable extends DataTable {
    public HostInitiatorDataTable() {
        addColumn("initiatorNode");
        addColumn("initiatorPort");
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        addColumn("protocol");
        sortAll();
        setDefaultSort("initiatorPort", "asc");
    }
}
