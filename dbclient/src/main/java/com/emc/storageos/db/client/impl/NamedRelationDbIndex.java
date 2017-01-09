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

public class NamedRelationDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(NamedRelationDbIndex.class);

    NamedRelationDbIndex(ColumnFamily<String, IndexColumnName> indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {
        String name = ((NamedURI) value).getName();

        ColumnListMutation<IndexColumnName> indexColList =
                mutator.getIndexColumnList(indexCF, getRowKey(column, value));

        IndexColumnName indexEntry =
                new IndexColumnName(className, name.toLowerCase(), name, recordKey, column.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
            String className, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        ColumnListMutation<IndexColumnName> indexColList =
                mutator.getIndexColumnList(indexCF, getRowKey(column));

        UUID uuid = column.getName().getTimeUUID();
        NamedURI namedURI = NamedURI.fromString(column.getStringValue());
        String name = namedURI.getName();

        IndexColumnName indexEntry =
                new IndexColumnName(className, name.toLowerCase(), name, recordKey, uuid);

        indexColList.deleteColumn(indexEntry);

        return true;
    }

    String getRowKey(CompositeColumnName column, Object value) {
        return ((NamedURI) value).getURI().toString();
    }

    String getRowKey(Column<CompositeColumnName> column) {
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
