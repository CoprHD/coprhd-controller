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

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import com.emc.storageos.db.client.model.*;

public class RelationDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(RelationIndex.class);

    RelationDbIndex(ColumnFamily<String, IndexColumnName> indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {
        String rowKey = getRowKey(column, value);

        ColumnListMutation<IndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, rowKey);

        UUID uuid = column.getTimeUUID();

        IndexColumnName indexEntry = new IndexColumnName(className, recordKey, uuid);

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        _log.info("db consistency check: added to RelationIndex, column1 = " + className +
                ", column2 = " + recordKey + ", uuid = " + uuid);

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

        indexColList.deleteColumn(new IndexColumnName(className, recordKey, uuid));

        _log.info("db consistency check: deleting from RelationIndex, column1 = " + className +
                ", column2 = " + recordKey + ", uuid = " + uuid);

        return true;
    }

    String getRowKey(CompositeColumnName column, Object value) {
        if (indexByKey) {
            return column.getTwo();
        }

        return ((URI) value).toString();
    }

    String getRowKey(Column<CompositeColumnName> column) {
        if (indexByKey) {
            return column.getName().getTwo();
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
