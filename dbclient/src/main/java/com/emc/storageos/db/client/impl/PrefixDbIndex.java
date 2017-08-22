/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import com.emc.storageos.db.client.model.*;

import java.util.List;
import java.util.Map;

public class PrefixDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(PrefixDbIndex.class);

    // minimum number of characters required for prefix indexing
    private int minPrefixChars;

    PrefixDbIndex(ColumnFamily<String, IndexColumnName> indexCF, int minChars) {
        super(indexCF);
        minPrefixChars = minChars;
    }

    public int getMinPrefixChars(){
        return minPrefixChars;
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {
        String text = (String) value;
        if (text.isEmpty() || text.length() < minPrefixChars) {
            _log.warn("String too short in prefix index field: {}", fieldName);
            return false;
        }

        String rowKey = getRowKey(column, text);

        ColumnListMutation<IndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, rowKey);

        IndexColumnName indexEntry =
                new IndexColumnName(className, text.toLowerCase(), text, recordKey, column.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
            String className, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        String text = column.getStringValue();
        if (text.isEmpty() || text.length() < minPrefixChars) {
            _log.warn("String too short in prefix index field: {}, value: {}", fieldName, text);
            return false;
        }

        String indexRowKey = getRowKey(column);

        ColumnListMutation<IndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, indexRowKey);

        CompositeColumnName columnName = column.getName();

        IndexColumnName indexEntry =
                new IndexColumnName(className, text.toLowerCase(), text, recordKey, columnName.getTimeUUID());

        indexColList.deleteColumn(indexEntry);

        return true;
    }

    public String getRowKey(String value) {
        if (value.length() < minPrefixChars) {
            _log.warn("Value is too short for prefix index : {}", value);
            return value;
        }

        return value.toLowerCase().substring(0, minPrefixChars);
    }

    String getRowKey(CompositeColumnName column, Object val) {
        String value = (String) val;
        return getRowKey(value);
    }

    String getRowKey(Column<CompositeColumnName> column) {
        return getRowKey(column.getStringValue());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("PrefixDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        builder.append("minPrefixChars:");
        builder.append(minPrefixChars);

        return builder.toString();
    }
}
