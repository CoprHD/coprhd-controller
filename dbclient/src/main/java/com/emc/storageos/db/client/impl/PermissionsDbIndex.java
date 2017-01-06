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

public class PermissionsDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(PermissionsDbIndex.class);

    PermissionsDbIndex(ColumnFamily<String, IndexColumnName> indexCF) {
        super(indexCF);
    }

    String getRowKey(CompositeColumnName column, Object value) {
        return column.getTwo();
    }

    String getRowKey(Column<CompositeColumnName> column) {
        return column.getName().getTwo();
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {
        String rowKey = getRowKey(column, value);

        ColumnListMutation<IndexColumnName> indexColList =
                mutator.getIndexColumnList(indexCF, rowKey);

        IndexColumnName indexEntry =
                new IndexColumnName(className, recordKey, value.toString(), column.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, value.toString(), ttl);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
            String className, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        String rowKey = getRowKey(column);

        ColumnListMutation<IndexColumnName> indexColList =
                mutator.getIndexColumnList(indexCF, rowKey);

        UUID uuid = column.getName().getTimeUUID();

        IndexColumnName indexEntry =
                new IndexColumnName(className, recordKey, column.getStringValue(), uuid);

        indexColList.deleteColumn(indexEntry);

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
