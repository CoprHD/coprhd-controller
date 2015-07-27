/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util.datatable;

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import play.mvc.Router;

import java.util.*;

public abstract class DataTable {

    /** The columns for this table. */
    private List<DataTableColumn> columns = new ArrayList<DataTableColumn>();
    /** The mapping of columns by name. */
    private Map<String, DataTableColumn> columnMap = new HashMap<String, DataTableColumn>();
    /** The mapping of column names to field names. */
    private Map<String, String> fieldNames = new HashMap<String, String>();
    /** The default sort field. */
    private String defaultSortField;
    /** The default sort order. */
    private String defaultSortOrder = "asc";
    /** Flag indicating whether this should use server side processing (defaults to false). */
    private boolean serverSide = false;
    /** Callback function for each row. */
    private String rowCallback;

    public DataTable() {

    }

    public List<DataTableColumn> getColumns() {
        return columns;
    }

    public Map<String, DataTableColumn> getColumnMap() {
        return columnMap;
    }

    public int getColumnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (name.equals(columns.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    public void setDefaultSort(String field, String order) {
        setDefaultSortField(field);
        setDefaultSortOrder(order);
    }

    public String getDefaultSortField() {
        return defaultSortField;
    }

    public void setDefaultSortField(String defaultSortField) {
        this.defaultSortField = defaultSortField;
    }

    public String getDefaultSortOrder() {
        return defaultSortOrder;
    }

    public void setDefaultSortOrder(String defaultSortOrder) {
        this.defaultSortOrder = defaultSortOrder;
    }

    public boolean isServerSide() {
        return serverSide;
    }

    public void setServerSide(boolean serverSide) {
        this.serverSide = serverSide;
    }

    public String getRowCallback() {
        return rowCallback;
    }

    public void setRowCallback(String rowCallback) {
        this.rowCallback = rowCallback;
    }

    public int getDefaultSortIndex() {
        if (StringUtils.isNotBlank(defaultSortField)) {
            return getColumnIndex(defaultSortField);
        }
        // Default to find the first sortable column
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).isSortable()) {
                return i;
            }
        }
        return -1;
    }

    public DataTableColumnConfiguration addColumn(String name) {
        DataTableColumn column = new DataTableColumn(name);
        return addColumn(column);
    }

    public DataTableColumnConfiguration addColumn(int index, DataTableColumn column) {
        columns.add(index, column);
        columnMap.put(column.getName(), column);
        return new DataTableColumnConfiguration(column);
    }

    public DataTableColumnConfiguration addColumn(DataTableColumn column) {
        columns.add(column);
        columnMap.put(column.getName(), column);
        return new DataTableColumnConfiguration(column);
    }

    public DataTableColumnConfiguration addColumn(String name, String fieldName) {
        setFieldName(name, fieldName);
        return addColumn(name);
    }

    public DataTableColumnConfiguration addColumns(String... names) {
        for (String name : names) {
            addColumn(name);
        }
        return alterColumns(names);
    }

    protected void setHidden(String... names) {
        alterColumns(names).hidden();
    }

    protected void setSortable(String... names) {
        alterColumns(names).setSortable(true);
    }

    protected void sortAll() {
        new DataTableColumnConfiguration(columns).setSortable(true);
    }

    protected void sortAllExcept(String... names) {
        Set<String> exclude = new HashSet<String>(Arrays.asList(names));
        List<DataTableColumn> sortColumns = new ArrayList<DataTableColumn>();
        for (DataTableColumn column : columns) {
            if (!exclude.contains(column.getName())) {
                sortColumns.add(column);
            }
        }
        new DataTableColumnConfiguration(sortColumns).setSortable(true);
    }

    protected Map<String, String> getFieldNames() {
        return this.fieldNames;
    }

    protected void setFieldName(String name, String sortField) {
        fieldNames.put(name, sortField);
    }

    public DataTableColumnConfiguration alterColumn(String name) {
        DataTableColumn column = columnMap.get(name);
        if (column == null) {
            throw new IllegalArgumentException("No such column '" + name + "'");
        }
        return new DataTableColumnConfiguration(column);
    }

    public DataTableColumnConfiguration alterColumns(String... names) {
        List<DataTableColumn> toAlter = new ArrayList<DataTableColumn>();
        for (String name : names) {
            DataTableColumn column = columnMap.get(name);
            if (column == null) {
                throw new IllegalArgumentException("No such column '" + name + "'");
            }
            toAlter.add(column);
        }
        return new DataTableColumnConfiguration(toAlter);
    }

    protected static String createLink(Class<?> controller, String action, String name, Object value) {
        return createLink(controller.getName() + "." + action, name, value);
    }

    protected static String createLink(String action, String name, Object value) {
        Map<String, Object> args = Maps.newHashMap();
        args.put(name, value);
        return Router.reverse(action, args).url;
    }
}
