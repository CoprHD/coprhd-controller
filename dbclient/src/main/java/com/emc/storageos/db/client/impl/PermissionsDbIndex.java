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

import com.emc.storageos.db.client.model.*;

public class PermissionsDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(PermissionsDbIndex.class);

    PermissionsDbIndex(ColumnFamilyDefinition indexCF) {
        super(indexCF);
    }

    String getRowKey(CompositeColumnName column, Object value) {
        return column.getTwo();
    }

    String getRowKey(CompositeColumnName column) {
        return column.getTwo();
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {
        String indexRowKey = getRowKey(column, value);
        IndexColumnName indexEntry = new IndexColumnName(className, recordKey, value.toString(), column.getTimeUUID());
        mutator.insertIndexColumn(indexCF.getName(), indexRowKey, indexEntry, value.toString());
        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutator mutator, Map<String, List<CompositeColumnName>> fieldColumnMap) {
        String rowKey = getRowKey(column);
        UUID uuid = column.getTimeUUID();
        IndexColumnName indexEntry = new IndexColumnName(className, recordKey, column.getStringValue(), uuid);

        mutator.deleteIndexColumn(indexCF.getName(), rowKey, indexEntry);
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("PermissionsDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        return builder.toString();
    }

}
