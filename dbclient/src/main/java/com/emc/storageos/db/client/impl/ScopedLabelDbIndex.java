/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import com.emc.storageos.db.client.model.*;

public class ScopedLabelDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(ScopedLabelDbIndex.class);

    // minimum number of characters required for prefix indexing
    private int minPrefixChars;

    ScopedLabelDbIndex(ColumnFamily<String, IndexColumnName> indexCF, int minChars) {
        super(indexCF);
        minPrefixChars = minChars;
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {
        ScopedLabel scopedLabel = (ScopedLabel) value;
        String label = scopedLabel.getLabel();

        if (label == null || label.length() < minPrefixChars) {
            _log.warn("String too short in scoped prefix index field: {}", fieldName);
            return false;
        }

        // scoped row key
        String scopedRowKey = getRowKey(column, scopedLabel);

        ColumnListMutation<IndexColumnName> indexColList =
                mutator.getIndexColumnList(indexCF, scopedRowKey);

        IndexColumnName indexEntry = new IndexColumnName(className, label.toLowerCase(),
                label, recordKey, column.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        // unscoped row key for global search
        String rowKey = getRowKey(label);
        indexColList = mutator.getIndexColumnList(indexCF, rowKey);
        indexEntry = new IndexColumnName(className,
                label.toLowerCase(), label, recordKey, column.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        return true;
    }

    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
            String className, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        UUID uuid = column.getName().getTimeUUID();

        String text = column.getStringValue();
        String label = ScopedLabel.fromString(text).getLabel();

        // delete scoped row
        String scopedRowKey = getRowKey(column);
        ColumnListMutation<IndexColumnName> indexColList =
                mutator.getIndexColumnList(indexCF, scopedRowKey);

        IndexColumnName indexEntry =
                new IndexColumnName(className, label.toLowerCase(), label, recordKey, uuid);

        indexColList.deleteColumn(indexEntry);

        // delete global row
        String rowKey = getRowKey(label);
        indexColList = mutator.getIndexColumnList(indexCF, rowKey);

        indexEntry = new IndexColumnName(className, label.toLowerCase(), label, recordKey, uuid);

        indexColList.deleteColumn(indexEntry);

        return true;
    }

    public String getRowKey(String val) {
        if (val.length() < minPrefixChars) {
            throw new IllegalArgumentException(String.format("Label prefix is \"%s\", shorter than required min length: %d", val,
                    minPrefixChars));
        }

        return val.toLowerCase().substring(0, minPrefixChars);
    }

    String getRowKey(CompositeColumnName column, Object value) {
        ScopedLabel val = (ScopedLabel) value;
        return getRowKey(val);
    }

    String getRowKey(Column<CompositeColumnName> column) {
        String text = column.getStringValue();
        ScopedLabel label = ScopedLabel.fromString(text);

        return getRowKey(label);
    }

    String getRowKey(ScopedLabel label) {
        if (label.getScope() != null) {
            return String.format("%s:%s", label.getScope(), getRowKey(label.getLabel()));
        } else {
            return getRowKey(label.getLabel());
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("ScopedLabelDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        builder.append("minPrefixChars:");
        builder.append(minPrefixChars);

        return builder.toString();
    }
}
