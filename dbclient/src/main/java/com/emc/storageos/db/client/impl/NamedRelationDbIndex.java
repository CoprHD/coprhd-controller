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

public class NamedRelationDbIndex extends DbIndex {
    private static final Logger _log = LoggerFactory.getLogger(NamedRelationDbIndex.class);

    NamedRelationDbIndex(ColumnFamily<String, IndexColumnName> indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutatorDS mutator, Integer ttl, DataObject obj) {
        String name = ((NamedURI) value).getName();

        String indexRowKey = getRowKey(column, value);
        IndexColumnName indexEntry = new IndexColumnName(className, name.toLowerCase(), name, recordKey, mutator.getTimeUUID());

        mutator.insertIndexColumn(indexCF.getName(), indexRowKey, indexEntry, null);
        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutatorDS mutator, Map<String, List<CompositeColumnName>> fieldColumnMap) {
        String rowKey = getRowKey(column);
        UUID uuid = column.getTimeUUID();
        NamedURI namedURI = NamedURI.fromString(column.getStringValue());
        String name = namedURI.getName();
        IndexColumnName indexEntry = new IndexColumnName(className, name.toLowerCase(), name, recordKey, uuid);

        mutator.deleteIndexColumn(indexCF.getName(), rowKey, indexEntry);
        return true;
    }

    String getRowKey(CompositeColumnName column, Object value) {
        return ((NamedURI) value).getURI().toString();
    }

    String getRowKey(CompositeColumnName column) {
        NamedURI namedURI = NamedURI.fromString(column.getStringValue());
        return namedURI.getURI().toString();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("NamedRelationDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        return builder.toString();
    }

}
