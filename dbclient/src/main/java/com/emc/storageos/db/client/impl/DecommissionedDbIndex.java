/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import org.apache.cassandra.serializers.BooleanSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import com.emc.storageos.db.client.model.*;

public class DecommissionedDbIndex extends DbIndex {
    private static final Logger _log = LoggerFactory.getLogger(DecommissionedDbIndex.class);

    DecommissionedDbIndex(ColumnFamily<String, IndexColumnName> indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
            String className, RowMutatorDS mutator, Integer ttl, DataObject obj) {

        String indexRowKey = className;
        IndexColumnName indexEntry = new IndexColumnName(value.toString(), recordKey, mutator.getTimeUUID());

        mutator.insertIndexColumn(indexCF.getName(), indexRowKey, indexEntry, null);
        return true;
    }

    @Override
    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutatorDS mutator, Map<String, List<CompositeColumnName>> fieldColumnMap) {
        String rowKey = className;
        UUID uuid = column.getTimeUUID();
        Boolean val = BooleanSerializer.instance.deserialize(column.getValue());

        mutator.deleteIndexColumn(indexCF.getName(), rowKey, new IndexColumnName(val.toString(), recordKey, uuid));
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
