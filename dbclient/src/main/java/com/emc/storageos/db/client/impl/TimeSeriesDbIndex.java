package com.emc.storageos.db.client.impl;

import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by brian on 16-11-16.
 */
public class TimeSeriesDbIndex extends DbIndex<TimeSeriesIndexColumnName> {
    private static final Logger _log = LoggerFactory.getLogger(TimeSeriesDbIndex.class);

    TimeSeriesDbIndex(ColumnFamily<String, TimeSeriesIndexColumnName> indexCF) {
        super(indexCF);
    }

    @Override
    boolean addColumn(String recordKey, CompositeColumnName column, Object value,
                      String className, RowMutator mutator, Integer ttl, DataObject obj) {
        if (value.toString().isEmpty()) {
            // empty string in alternate id field, ignore and continue
            _log.warn("Empty string in {} id field: {}", this.getClass().getSimpleName(), fieldName);
            return false;
        }

        ColumnListMutation<TimeSeriesIndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, className);

        _log.info("lbytt0: add indexKey={} key={}", className, recordKey);

        TimeSeriesIndexColumnName indexEntry = new TimeSeriesIndexColumnName(recordKey, mutator.getTimeUUID());

        ColumnValue.setColumn(indexColList, indexEntry, null, ttl);

        return true;
    }

    @Override
    boolean removeColumn(String recordKey, Column<CompositeColumnName> column,
                         String className, RowMutator mutator,
                         Map<String, List<Column<CompositeColumnName>>> fieldColumnMap) {
        UUID uuid = column.getName().getTimeUUID();

        //String rowKey = getRowKey(column);
        String rowKey = className;

        ColumnListMutation<TimeSeriesIndexColumnName> indexColList = mutator.getIndexColumnList(indexCF, rowKey);

        _log.info("lbyf0 to delete rowKey={} stack=", rowKey, new Throwable());
        indexColList.deleteColumn(new TimeSeriesIndexColumnName(recordKey, uuid));

        return true;
    }

    /*
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
    */

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("AltIdDbIndex class");
        builder.append("\t");
        builder.append(super.toString());
        builder.append("\n");

        return builder.toString();
    }
}
