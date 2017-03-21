/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.*;

public class RelationDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(RelationIndex.class);

    RelationDbIndex(ColumnFamilyDefinition indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {
        String indexRowKey = getRowKey(column, value);
        IndexColumnName indexEntry = new IndexColumnName(className, recordKey, column.getTimeUUID());
        mutator.insertIndexColumn(indexCF.getName(), indexRowKey, indexEntry, null);
        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutator mutator, Map<String, List<CompositeColumnName>> fieldColumnMap) {
        String rowKey = getRowKey(column);
        UUID uuid = column.getTimeUUID();

        mutator.deleteIndexColumn(indexCF.getName(), rowKey, new IndexColumnName(className, recordKey, uuid));
        return true;
    }

    String getRowKey(CompositeColumnName column, Object value) {
        if (indexByKey) {
            return column.getTwo();
        }

        return ((URI) value).toString();
    }

    String getRowKey(CompositeColumnName column) {
        if (indexByKey) {
            return column.getTwo();
        }

        return column.getStringValue();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("RelationDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        return builder.toString();
    }

}
