/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.model.ColumnFamily;

import com.emc.storageos.db.client.model.*;

import java.util.List;
import java.util.Map;

public class PrefixDbIndex extends DbIndex {
    private static final Logger _log = LoggerFactory.getLogger(PrefixDbIndex.class);

    // minimum number of characters required for prefix indexing
    private int minPrefixChars;

    PrefixDbIndex(ColumnFamily<String, IndexColumnName> indexCF, int minChars) {
        super(indexCF);
        minPrefixChars = minChars;
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutatorDS mutator, Integer ttl, DataObject obj) {
        String text = (String) value;
        if (text.isEmpty() || text.length() < minPrefixChars) {
            _log.warn("String too short in prefix index field: {}", fieldName);
            return false;
        }

        String indexRowKey = getRowKey(column, text);
        IndexColumnName indexEntry = new IndexColumnName(className, text.toLowerCase(), text, recordKey, mutator.getTimeUUID());

        mutator.insertIndexColumn(indexCF.getName(), indexRowKey, indexEntry, null);
        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutatorDS mutator, Map<String, List<CompositeColumnName>> fieldColumnMap) {
        String text = column.getStringValue();
        if (text.isEmpty() || text.length() < minPrefixChars) {
            _log.warn("String too short in prefix index field: {}, value: {}", fieldName, text);
            return false;
        }

        String indexRowKey = getRowKey(column);
        IndexColumnName indexEntry = new IndexColumnName(className, text.toLowerCase(), text, recordKey, column.getTimeUUID());

        mutator.deleteIndexColumn(indexCF.getName(), indexRowKey, indexEntry);
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

    String getRowKey(CompositeColumnName column) {
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
