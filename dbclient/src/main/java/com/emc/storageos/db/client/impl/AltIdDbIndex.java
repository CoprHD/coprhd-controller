/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.model.*;

public class AltIdDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(AltIdDbIndex.class);

    AltIdDbIndex(ColumnFamilyDefinition indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {
        if (value.toString().isEmpty()) {
            // empty string in alternate id field, ignore and continue
            _log.warn("Empty string in alternate id field: {}", fieldName);
            return false;
        }

        String indexRowKey = getRowKey(column, value);
        IndexColumnName indexEntry = new IndexColumnName(className, recordKey, column.getTimeUUID());
        mutator.insertIndexColumn(indexCF.getName(), indexRowKey, indexEntry, null);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutator mutator, Map<String, List<CompositeColumnName>> fieldColumnMap) {
        UUID uuid = column.getTimeUUID();
        String rowKey = getRowKey(column);

        mutator.deleteIndexColumn(indexCF.getName(), rowKey, new IndexColumnName(className, recordKey, uuid));
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
