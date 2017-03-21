/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DataObject;

public class ClassNameTimeSeriesDBIndex extends DbIndex<ClassNameTimeSeriesIndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(ClassNameTimeSeriesDBIndex.class);

    ClassNameTimeSeriesDBIndex(ColumnFamilyDefinition indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
                      String className, RowMutator mutator, Integer ttl, DataObject obj) {
        if (value.toString().isEmpty()) {
            // empty string in alternate id field, ignore and continue
            _log.warn("Empty string in field: {}", fieldName);
            return false;
        }

        String rowKey = getRowKey(column, value);
        ClassNameTimeSeriesIndexColumnName indexEntry = new ClassNameTimeSeriesIndexColumnName(className, recordKey, column.getTimeUUID());
        mutator.insertIndexColumn(indexCF.getName(), rowKey, indexEntry, null);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
            RowMutator mutator, Map<String, List<CompositeColumnName>> fieldColumnMap) {
        UUID uuid = column.getTimeUUID();

        String rowKey = getRowKey(column);
        mutator.deleteIndexColumn(indexCF.getName(), rowKey, new ClassNameTimeSeriesIndexColumnName(className, recordKey, uuid));

        return true;
    }

    String getRowKey(CompositeColumnName column, Object value) {
        if (indexByKey) {
            return column.getTwo();
        }

        return value.toString();
    }

    String getRowKey(CompositeColumnName column) {
        if (indexByKey) {
            return column.getTwo();
        }

        return column.getStringValue();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("AltIdDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        return builder.toString();
    }
}