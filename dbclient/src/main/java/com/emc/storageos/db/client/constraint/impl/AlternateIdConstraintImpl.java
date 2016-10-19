/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;
import java.util.Date;

import com.netflix.astyanax.util.TimeUUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Alternate ID constraint implementation
 */
public class AlternateIdConstraintImpl extends ConstraintImpl implements AlternateIdConstraint {
    private static final Logger log = LoggerFactory.getLogger(AlternateIdConstraintImpl.class);

    private final ColumnFamily<String, IndexColumnName> _altIdCf;
    private final String _altId;
    private final Class<? extends DataObject> _entryType;
    private long startTimeMicros;
    private long endTimeMicros;

    private Keyspace _keyspace;

    public AlternateIdConstraintImpl(ColumnField field, String altId) {
        this(field, altId, 0, 0);
    }

    public AlternateIdConstraintImpl(ColumnField field, String altId, long sTimeInMicros, long eTimeInMicros) {
        super(field, altId);

        _altIdCf = field.getIndexCF();
        _altId = altId;
        _entryType = field.getDataObjectType();
        startTimeMicros = sTimeInMicros*1000L;
        endTimeMicros = eTimeInMicros* 1000L;
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        queryOnePageWithoutAutoPaginate(genQuery(), _entryType.getSimpleName(), result);
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                .withColumnRange(CompositeColumnNameSerializer.get().buildRange()
                        .greaterThanEquals(_entryType.getSimpleName())
                        .lessThanEquals(_entryType.getSimpleName())
                        .limit(pageCount));
        return query;
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> QueryHitIterator<T> getQueryHitIterator(RowQuery<String, IndexColumnName> query,
                                                          final QueryResult<T> result) {
        FilteredQueryHitIterator<T> it = new FilteredQueryHitIterator<T>(query) {
            @Override
            protected T createQueryHit(Column<IndexColumnName> column) {
                return result.createQueryHit(getURI(column));
            }

            @Override
            public boolean filter(Column<IndexColumnName> column) {
                long timeMarked = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());

                log.info("starTime={} ({})", startTimeMicros, new Date(startTimeMicros));
                log.info("endTime= {} ({})", endTimeMicros, new Date(endTimeMicros));
                log.info("marktime= {} ({})", timeMarked, new Date(timeMarked));

                return (startTimeMicros <= 0 && endTimeMicros <= 0) || isInTimeRange(timeMarked);
            }
        };

        return it;
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        log.info("starTime={} endTime={}", startTimeMicros, endTimeMicros);
        if (startTimeMicros != 0 && endTimeMicros != 0) {
            long timeMarked = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());
            log.info("marked time={}", timeMarked);
            if (!isInTimeRange(timeMarked)) {
                log.info("Not in range result={} stack=", result, new Throwable());
                return null;
            }
        }

        return result.createQueryHit(getURI(column));
    }

    private boolean isInTimeRange(long timeMarked) {
        return (startTimeMicros > 0) && (endTimeMicros > 0)
                &&  (startTimeMicros < timeMarked) && (timeMarked < endTimeMicros);
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _entryType;
    }

	@Override
	public boolean isValid() {
        return this._altId!=null && !this._altId.isEmpty();
	}
}
