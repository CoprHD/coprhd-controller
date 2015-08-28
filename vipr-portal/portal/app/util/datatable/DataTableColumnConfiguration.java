/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.datatable;

import java.util.Collections;
import java.util.List;

public class DataTableColumnConfiguration {
    private List<DataTableColumn> columns;

    public DataTableColumnConfiguration(DataTableColumn column) {
        this.columns = Collections.singletonList(column);
    }

    public DataTableColumnConfiguration(List<DataTableColumn> columns) {
        this.columns = columns;
    }

    public DataTableColumnConfiguration setCssClass(String cssClass) {
        for (DataTableColumn column : columns) {
            column.setCssClass(cssClass);
        }
        return this;
    }

    public DataTableColumnConfiguration setSortable(boolean sortable) {
        for (DataTableColumn column : columns) {
            column.setSortable(sortable);
        }
        return this;
    }

    public DataTableColumnConfiguration setSearchable(boolean searchable) {
        for (DataTableColumn column : columns) {
            column.setSearchable(searchable);
        }
        return this;
    }

    public DataTableColumnConfiguration setVisible(boolean visible) {
        for (DataTableColumn column : columns) {
            column.setVisible(visible);
        }
        return this;
    }

    public DataTableColumnConfiguration setRenderFunction(String renderFunction) {
        for (DataTableColumn column : columns) {
            column.setRenderFunction(renderFunction);
        }
        return this;
    }

    public DataTableColumnConfiguration setProperty(String property) {
        for (DataTableColumn column : columns) {
            column.setProperty(property);
        }
        return this;
    }

    public DataTableColumnConfiguration hidden() {
        setVisible(false);
        return this;
    }

    public DataTableColumnConfiguration sortable() {
        setSortable(true);
        return this;
    }

    public DataTableColumnConfiguration sortDataColumn(int columnIndex) {
        for (DataTableColumn column : columns) {
            column.setSortDataColumn(columnIndex);
        }
        return this;
    }
}
