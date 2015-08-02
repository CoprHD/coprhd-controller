/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.datatable;

public class EmptyColumn extends DataTableColumn {
    public EmptyColumn(String name) {
        super(name);
        setProperty(null);
        setSortable(false);
        setCssClass("dummy");
    }
}
