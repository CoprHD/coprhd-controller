/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.RelationDbIndex;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.util.TimeUUIDUtils;

import java.net.URI;

/**
 * A containment constraint that returns only those elements from the index that were added between startTime and endTime
 */
public class TimedContainmentConstraintImpl extends ConstraintImpl {
    private static final long MILLIS_TO_MICROS = 1000L;

    private final long startTimeMicros;
    private final long endTimeMicros;

    private URI indexKey;
    private Class<? extends DataObject> entryType;
    private final ColumnField field;
    private Keyspace keyspace;

    public TimedContainmentConstraintImpl(URI indexKey, long startTimeMicros, long endTimeMicros, Class<? extends DataObject> entryType,
            ColumnField field) {
        super(indexKey, entryType, field);
        this.startTimeMicros = startTimeMicros * MILLIS_TO_MICROS;
        this.endTimeMicros = endTimeMicros * MILLIS_TO_MICROS;

        this.indexKey = indexKey;
        this.entryType = entryType;
        this.field = field;
        returnOnePage = true;
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        this.keyspace = keyspace;
    }

    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        RowQuery<String, IndexColumnName> query = keyspace.prepareQuery(field.getIndexCF())
                .getKey(indexKey.toString())
                .withColumnRange(
                        CompositeColumnNameSerializer.get().buildRange()
                                .greaterThanEquals(entryType.getSimpleName())
                                .lessThanEquals(entryType.getSimpleName()));

        QueryHitIterator<T> it = createQueryHitIterator(query, result);
        query.autoPaginate(true);
        it.prime();
        result.setResult(it);
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query = keyspace
                .prepareQuery(field.getIndexCF())
                .getKey(indexKey.toString())
                .withColumnRange(
                        CompositeColumnNameSerializer.get().buildRange()
                                .greaterThanEquals(entryType.getSimpleName())
                                .lessThanEquals(entryType.getSimpleName())
                                .limit(pageCount));
        return query;

    }

    protected <T> QueryHitIterator<T> createQueryHitIterator(RowQuery<String, IndexColumnName> query, final QueryResult<T> result) {

        return new FilteredQueryHitIterator<T>(query) {
            @Override
            protected T createQueryHit(Column<IndexColumnName> column) {
                return result.createQueryHit(getURI(column), column.getName().getThree(), column.getName().getTimeUUID());
            }

            @Override
            public boolean filter(Column<IndexColumnName> column) {
                long columnTime = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());
                // Filtering on startTime, startTime = -1 for no filtering
                if (startTimeMicros > 0 && columnTime < startTimeMicros) {
                    return false;
                }
                // Filtering on endTime, endTime = -1 for no filtering
                if (endTimeMicros > 0 && columnTime > endTimeMicros) {
                    return false;
                }
                return true;
            }
        };
    }

    @Override
    protected <T> T createQueryHit(QueryResult<T> result, Column<IndexColumnName> column) {
        return result.createQueryHit(getURI(column), column.getName().getThree(), column.getName().getTimeUUID());
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        URI ret;
        if (field.getIndex() instanceof RelationDbIndex) {
            ret = URI.create(col.getName().getTwo());
        } else {
            ret = URI.create(col.getName().getFour());
        }

        return ret;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return field.getDataObjectType();
    }
    
	@Override
	public boolean isValid() {
        return this.indexKey!=null && !this.indexKey.toString().isEmpty();
	}
}
