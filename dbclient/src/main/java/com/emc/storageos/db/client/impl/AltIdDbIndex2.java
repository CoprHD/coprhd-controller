package com.emc.storageos.db.client.impl;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import com.netflix.astyanax.util.TimeUUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import com.emc.storageos.db.client.model.*;

public class AltIdDbIndex2 extends DbIndex<IndexColumnName2> {
    private static final Logger _log = LoggerFactory.getLogger(AltIdDbIndex2.class);

    AltIdDbIndex2(ColumnFamily<String, IndexColumnName2> indexCF) {
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

        String rowKey = getRowKey(column, value);

        ColumnListMutation<IndexColumnName2> indexColList = mutator.getIndexColumnList(indexCF, rowKey);

        _log.info("lbytt0: add {} key={} inactive={}", className, recordKey, obj.getInactive());

        //IndexColumnName2 indexEntry = new IndexColumnName2(className, recordKey, mutator.getTimeUUID());
        IndexColumnName2 indexEntry = new IndexColumnName2(className, recordKey, obj.getInactive(), mutator.getTimeStamp());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
                         String className, RowMutator mutator,
                         Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        UUID uuid = column.getName().getTimeUUID();
        long timestamp = TimeUUIDUtils.getMicrosTimeFromUUID(uuid);

        String rowKey = getRowKey(column);

        ColumnListMutation<IndexColumnName2> indexColList = mutator.getIndexColumnList(indexCF, rowKey);

        _log.info("lbyf0 to delete rowKey={} stack=", rowKey, new Throwable());
        indexColList.deleteColumn(new IndexColumnName2(className, recordKey, timestamp));

        return true;
    }

    String getRowKey(CompositeColumnName column, Object value) {
        if (indexByKey) {
            return column.getTwo();
        }

        return value.toString();
    }

    String getRowKey(Column<CompositeColumnName> column) {
        if (indexByKey) {
            return column.getName().getTwo();
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