/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

/**
 */
public class AuditLogDataTable extends DataTable {

    public AuditLogDataTable() {

        addColumn("_timeInMillis").hidden().setSearchable(false);
        addColumn("_timeInMillis").setCssClass("time").setRenderFunction("renderTime");
        addColumn("_serviceType");
        addColumn("_userId");
        addColumn("_operationalStatus").setCssClass("result").setRenderFunction("renderResult");
        addColumn("_description").setCssClass("description").setRenderFunction("renderDescription");
        setDefaultSort("_timeInMillis", "desc");
        sortAllExcept("_description");
        this.setServerSide(true);
    }
}

