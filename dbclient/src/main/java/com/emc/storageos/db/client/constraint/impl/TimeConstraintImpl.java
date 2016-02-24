/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.util.RangeBuilder;
import com.netflix.astyanax.util.TimeUUIDUtils;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

import java.net.URI;
import java.util.Date;

/**
 * Constraint to query indexed columns on a start/end time. This uses the same
 * index as DecomissionedConstraintImpl but is designed to work on other column
 * families. This constraint takes a Start and End time and returns records
 * between this time period.
 */
public class TimeConstraintImpl extends ConstraintImpl implements DecommissionedConstraint {
    private static final long MILLIS_TO_MICROS = 1000L;
    private static final int DEFAULT_PAGE_SIZE = 100;
    private Keyspace keyspace;
    private final ColumnFamily<String, IndexColumnName> cf;
    private final String rowKey;
    private final long startTimeMicros;
    private final long endTimeMicros;
    private Boolean value = null;
    private Class<? extends DataObject> entityType;

    /**
     * Constructs the time constraint.
     * 
     * @param clazz DataObject class
     * @param cf Column Family
     * @param startTimeMillis Start time in milliseconds or -1 for no filtering on start time.
     * @param endTimeMillis End time in milliseconds or -1 for no filtering on end time.
     */
    public TimeConstraintImpl(Class<? extends DataObject> clazz, ColumnFamily<String, IndexColumnName> cf,
            Boolean value, long startTimeMillis, long endTimeMillis) {
        this.cf = cf;
        rowKey = clazz.getSimpleName();
        this.startTimeMicros = startTimeMillis * MILLIS_TO_MICROS;
        this.endTimeMicros = endTimeMillis * MILLIS_TO_MICROS;
        this.value = value;
        this.entityType = clazz;
    }

    /**
     * Constructs the time constraint.
     * 
     * @param clazz DataObject class
     * @param cf Column Family
     * @param startTime Start time Date or null for no filtering on start time
     * @param endTime End time Date or null for no filtering on end time
     */
    public TimeConstraintImpl(Class<? extends DataObject> clazz, Boolean value,
            ColumnFamily<String, IndexColumnName> cf, Date startTime, Date endTime) {
        this(clazz, cf, value,
                startTime == null ? -1 : startTime.getTime(),
                endTime == null ? -1 : endTime.getTime());
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        this.keyspace = keyspace;
    }

    @Override
    public <T> void execute(final QueryResult<T> result) {
        RowQuery<String, IndexColumnName> query;
        if (value == null) {
            query = keyspace.prepareQuery(cf).getKey(rowKey)
                    .autoPaginate(true)
                    .withColumnRange(new RangeBuilder().setLimit(DEFAULT_PAGE_SIZE).build());
        }
        else {
            query = keyspace.prepareQuery(cf).getKey(rowKey)
                    .autoPaginate(true)
                    .withColumnRange(
                            CompositeColumnNameSerializer.get().buildRange()
                                    .greaterThanEquals(value.toString())
                                    .lessThanEquals(value.toString())
                                    .limit(DEFAULT_PAGE_SIZE));
        }

        FilteredQueryHitIterator<T> it = new FilteredQueryHitIterator<T>(query) {
            @Override
            protected T createQueryHit(Column<IndexColumnName> column) {
                return result.createQueryHit(URI.create(column.getName().getTwo()));
            }

            @Override
            public boolean filter(Column<IndexColumnName> column) {
                long timeMarked = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());
                // Filtering on startTime, startTime = -1 for no filtering
                if (startTimeMicros > 0 && timeMarked < startTimeMicros) {
                    return false;
                }
                // Filtering on endTime, endTime = -1 for no filtering
                if (endTimeMicros > 0 && timeMarked > endTimeMicros) {
                    return false;
                }
                return true;
            }
        };

        it.prime();
        result.setResult(it);
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        queryOnePageWithoutAutoPaginate(genQuery(), Boolean.toString(value), result);
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        return result.createQueryHit(URI.create(column.getName().getTwo()));
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query;
        if (value == null) {
            query = keyspace.prepareQuery(cf).getKey(rowKey)
                    .withColumnRange(new RangeBuilder().setLimit(pageCount).build());
        } else {
            query = keyspace.prepareQuery(cf).getKey(rowKey)
                    .withColumnRange(
                            CompositeColumnNameSerializer.get().buildRange()
                                    .greaterThanEquals(value.toString())
                                    .lessThanEquals(value.toString())
                                    .limit(pageCount));
        }

        return query;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return entityType;
    }
    
	@Override
	public boolean isValid() {
        return rowKey!=null && !rowKey.isEmpty();
	}
}
