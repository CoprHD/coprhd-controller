/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.TimeSeriesConstraint;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.DataObject;

public class TimeSeriesConstraintImpl extends ConstraintImpl
        implements TimeSeriesConstraint {
    private static final Logger log = LoggerFactory.getLogger(TimeSeriesConstraintImpl.class);

    private final ColumnFamilyDefinition indexCF;
    private final Class<? extends DataObject> entryType;
    private final String indexKey;
    private long startTimeMicros;
    private long endTimeMicros;
    private long lastMatchedTimeStamp = 0;

    public TimeSeriesConstraintImpl(String indexKey, ColumnField field, long startTimeInMS, long endTimeInMS) {
        super(field);

        indexCF = field.getIndexCF();
        this.indexKey = indexKey;
        entryType = field.getDataObjectType();
        startTimeMicros = startTimeInMS*1000L;
        endTimeMicros = endTimeInMS * 1000L;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
    	queryOnePageWithAutoPaginate(genQueryStatement(), result);
    }

    public long getLastMatchedTimeStamp() {
        return lastMatchedTimeStamp;
    }

    @Override
    protected RowQuery<String, TimeSeriesIndexColumnName> genQuery() {
        return genQuery(genRangeBuilder().limit(pageCount));
    }

    private RowQuery<String, TimeSeriesIndexColumnName> genQuery(CompositeRangeBuilder builder) {
        return _keyspace.prepareQuery(indexCF).getKey(indexKey).withColumnRange(builder);
    }

    private CompositeRangeBuilder genRangeBuilder() {
        return TimeSeriesColumnNameSerializer.get().buildRange()
                .withPrefix(entryType.getSimpleName())
                .greaterThan(startTimeMicros)
                .lessThanEquals(endTimeMicros);
    }

    @Override
    public long count() throws DriverException {
        try {
            OperationResult<Integer> countResult = genQuery(genRangeBuilder()).getCount().execute();
            return countResult.getResult();
        }catch (ConnectionException e) {
            log.error("Failed to get count e=", e);
            throw e;
        }
    }

    @Override
    protected URI getURI(Column<TimeSeriesIndexColumnName> col) {
        return URI.create(col.getName().getThree());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<TimeSeriesIndexColumnName> column) {
        lastMatchedTimeStamp = column.getName().getTimeInMicros()/1000;
        return result.createQueryHit(getURI(column));
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return entryType;
    }

    @Override
    public boolean isValid() {
        return indexCF != null && entryType != null;
    }
}
