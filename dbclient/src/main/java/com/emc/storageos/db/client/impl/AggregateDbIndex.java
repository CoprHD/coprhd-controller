/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import org.apache.commons.lang3.StringUtils;
import java.util.*;

public class AggregateDbIndex extends DbIndex<IndexColumnName> {
    private boolean groupGlobal;
    private String[] groupBy;
    private Map<String, ColumnField> groupByFields;

    AggregateDbIndex(ColumnFamilyDefinition indexCF) {
        super(indexCF);
    }

    AggregateDbIndex(ColumnFamilyDefinition indexCF, String groupBy, boolean global) {
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
            String indexRowKey = className;
            mutator.insertIndexColumn(indexCF.getName(), indexRowKey, indexEntry, value);
        }

        for (String field : groupBy) {
            ColumnField colField = groupByFields.get(field);
            if (colField != null && obj.isInitialized(field)) {
                Object groupValue = ColumnField.getFieldValue(colField, obj);
                if (groupValue != null) {
                    String indexRowKey = getRowKey(className, groupValue);
                    mutator.insertIndexColumn(indexCF.getName(), indexRowKey, indexEntry, value);
                }
            }
        }

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutator mutator, Map<String, List<CompositeColumnName>> fieldColumnMap) {

        IndexColumnName indexField = new IndexColumnName(fieldName, recordKey, column.getTimeUUID());

        if (groupGlobal) {
            mutator.deleteIndexColumn(indexCF.getName(), className, indexField);
        }

        for (String group : groupBy) {
            ColumnField field = groupByFields.get(group);
            List<CompositeColumnName> groupByColumns = fieldColumnMap.get(field.getName());
            if (groupByColumns != null) {
                for (CompositeColumnName groupByCol : groupByColumns) {
                    Object groupValue = ColumnValue.getPrimitiveColumnValue(groupByCol.getValue(), field.getPropertyDescriptor());
                    if (groupValue != null) {
                        mutator.deleteIndexColumn(indexCF.getName(), getRowKey(className, groupValue), indexField);
                    }
                }
            }
        }
        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutator mutator, Map<String, List<CompositeColumnName>> fieldColumnMap,
                         DataObject obj) {

        IndexColumnName indexField = new IndexColumnName(fieldName, recordKey, (UUID) null);

        for (String group : groupBy) {
            ColumnField field = groupByFields.get(group);
            List<CompositeColumnName> groupByColumns = fieldColumnMap.get(field.getName());
            if (groupByColumns != null) {
                Object latestValue = null;
                if (obj != null) {
                    latestValue = ColumnField.getFieldValue(field, obj);
                }
                for (CompositeColumnName groupByCol : groupByColumns) {
                    Object groupValue = ColumnValue.getPrimitiveColumnValue(groupByCol.getValue(), field.getPropertyDescriptor());
                    if (groupValue != null && !groupValue.equals(latestValue)) {
                        mutator.deleteIndexColumn(indexCF.getName(), getRowKey(className, groupValue), indexField);
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
