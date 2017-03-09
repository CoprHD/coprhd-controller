/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.uimodels.Order;

public class TimeSeriesDbIndex extends DbIndex<TimeSeriesIndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(TimeSeriesDbIndex.class);
    TimeSeriesDbIndex(ColumnFamily<String, TimeSeriesIndexColumnName> indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
                      String className, RowMutator mutator, Integer ttl, DataObject obj) {
        if (value.toString().isEmpty()) {
            _log.warn("Empty string in {} id field: {}", this.getClass().getSimpleName(), fieldName);
            return false;
        }

        if (!(obj instanceof Order)) {
            throw new RuntimeException("Can not create TimeSeriesIndex on non Order object");
        }

        Order order = (Order)obj;
        String indexKey = order.getTenant();
        ColumnListMutation<TimeSeriesIndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, indexKey);

        TimeSeriesIndexColumnName indexEntry = new TimeSeriesIndexColumnName(className, recordKey, column.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
                         String className, RowMutator mutator,
                         Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        UUID uuid = column.getName().getTimeUUID();

        if (!className.equals(Order.class.getSimpleName())) {
            throw new RuntimeException("Can not remove TimeSeriesIndex on non Order object");
        }

        List<Column<CompositeColumnName>> value = fieldColumnMap.get("tenant");
        Column<CompositeColumnName> tenantCol = value.get(0);
        String tid = tenantCol.getStringValue();

        ColumnListMutation<TimeSeriesIndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, tid);

        TimeSeriesIndexColumnName col = new TimeSeriesIndexColumnName(className, recordKey, uuid);
        indexColList.deleteColumn(col);

        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getCanonicalName());
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        return builder.toString();
    }
}
