/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import com.emc.storageos.db.client.model.*;

public class ClassNameTimeSeriesDBIndex extends DbIndex<ClassNameTimeSeriesIndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(ClassNameTimeSeriesDBIndex.class);

    ClassNameTimeSeriesDBIndex(ColumnFamily<String, ClassNameTimeSeriesIndexColumnName> indexCF) {
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

        ColumnListMutation<ClassNameTimeSeriesIndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, rowKey);

        ClassNameTimeSeriesIndexColumnName indexEntry = new ClassNameTimeSeriesIndexColumnName(className, recordKey, column.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
                         String className, RowMutator mutator,
                         Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        UUID uuid = column.getName().getTimeUUID();

        String rowKey = getRowKey(column);

        ColumnListMutation<ClassNameTimeSeriesIndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, rowKey);

        indexColList.deleteColumn(new ClassNameTimeSeriesIndexColumnName(className, recordKey, uuid));

        return true;
    }

    String getRowKey(CompositeColumnName column, Object value) {
        if (indexByKey) {
            return column.getTwo();
        }

        return value.toString();
    }

    String getRowKey(Column<CompositeColumnName> column) {
        if (indexByKey) {
            return column.getName().getTwo();
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