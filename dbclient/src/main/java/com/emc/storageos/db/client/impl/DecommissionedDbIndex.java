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

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import com.emc.storageos.db.client.model.*;

public class DecommissionedDbIndex extends DbIndex<IndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(DecommissionedDbIndex.class);

    DecommissionedDbIndex(ColumnFamily<String, IndexColumnName> indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutator mutator, Integer ttl, DataObject obj) {

        ColumnListMutation<IndexColumnName> indexColList =
                mutator.getIndexColumnList(indexCF, className);

        IndexColumnName indexEntry = new IndexColumnName(value.toString(), recordKey, column.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
            String className, RowMutator mutator,
            Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        ColumnListMutation<IndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, className);

        UUID uuid = column.getName().getTimeUUID();
        Boolean val = column.getBooleanValue();

        indexColList.deleteColumn(new IndexColumnName(val.toString(), recordKey, uuid));

        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("DecommisionedDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        return builder.toString();
    }
}
