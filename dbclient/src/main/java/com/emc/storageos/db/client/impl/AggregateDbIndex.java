/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import org.apache.commons.lang3.StringUtils;
import java.util.*;

public class AggregateDbIndex extends DbIndex<IndexColumnName> {
    private boolean groupGlobal;
    private String[] groupBy;
    private Map<String, ColumnField> groupByFields;

    AggregateDbIndex(ColumnFamily<String, IndexColumnName> indexCF) {
        super(indexCF);
    }

    AggregateDbIndex(ColumnFamily<String, IndexColumnName> indexCF, String groupBy, boolean global) {
        super(indexCF);
        this.groupGlobal = global;
        if (StringUtils.isEmpty(groupBy)) {
            this.groupBy = new String[0];
            groupByFields = new HashMap<>();
        }
        else {
            this.groupBy = groupBy.split(",");
            groupByFields = new HashMap<>(this.groupBy.length);
        }
    }

    void addGroupByField(ColumnField field) {
        groupByFields.put(field.getName(), field);
    }

    Map<String, ColumnField> getGroupByFields() {
        return Collections.unmodifiableMap(groupByFields);
    }

    String[] getGroupBy() {
        return Arrays.copyOf(groupBy, groupBy.length);
    }

    boolean isGroupedGlobal() {
        return groupGlobal;
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {

        IndexColumnName indexEntry = new IndexColumnName(fieldName, recordKey, (UUID) null);

        if (groupGlobal) {
            ColumnListMutation<IndexColumnName> indexColList =
                    mutator.getIndexColumnList(indexCF, className);
            ColumnValue.setColumn(indexColList, indexEntry, value, ttl);
        }

        for (String field : groupBy) {
            ColumnField colField = groupByFields.get(field);
            if (colField != null && obj.isInitialized(field)) {
                Object groupValue = ColumnField.getFieldValue(colField, obj);
                if (groupValue != null) {
                    ColumnListMutation<IndexColumnName> indexColList =
                            mutator.getIndexColumnList(indexCF, getRowKey(className, groupValue));
                    ColumnValue.setColumn(indexColList, indexEntry, value, ttl);
                }
            }
        }

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
            String className, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {

        IndexColumnName indexField = new IndexColumnName(fieldName, recordKey, column.getName().getTimeUUID());

        if (groupGlobal) {
            mutator.getIndexColumnList(indexCF, className).deleteColumn(indexField);
        }

        for (String group : groupBy) {
            ColumnField field = groupByFields.get(group);
            List<Column<CompositeColumnName>> groupByColumns = fieldColumnMap.get(field.getName());
            if (groupByColumns != null) {
                for (Column<CompositeColumnName> groupByCol : groupByColumns) {
                    Object groupValue = ColumnValue.getPrimitiveColumnValue(groupByCol, field.getPropertyDescriptor());
                    if (groupValue != null) {
                        mutator.getIndexColumnList(indexCF, getRowKey(className, groupValue)).
                                deleteColumn(indexField);
                    }
                }
            }
        }
        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
            String className, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap,
            DataObject obj) {

        IndexColumnName indexField = new IndexColumnName(fieldName, recordKey, (UUID) null);

        for (String group : groupBy) {
            ColumnField field = groupByFields.get(group);
            List<Column<CompositeColumnName>> groupByColumns = fieldColumnMap.get(field.getName());
            if (groupByColumns != null) {
                Object latestValue = null;
                if (obj != null) {
                    latestValue = ColumnField.getFieldValue(field, obj);
                }
                for (Column<CompositeColumnName> groupByCol : groupByColumns) {
                    Object groupValue = ColumnValue.getPrimitiveColumnValue(groupByCol, field.getPropertyDescriptor());
                    if (groupValue != null && !groupValue.equals(latestValue)) {
                        mutator.getIndexColumnList(indexCF, getRowKey(className, groupValue)).
                                deleteColumn(indexField);
                    }
                }
            }
        }
        return true;
    }

    String getRowKey(String className, Object value) {
        if (value instanceof NamedURI) {
            return String.format("%s:%s", className, ((NamedURI) value).getURI().toString());
        } else {
            return String.format("%s:%s", className, value.toString());
        }
    }

    @Override
    public boolean needConsistency() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("DecommisionedDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        return builder.toString();
    }
}
