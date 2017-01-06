/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.serializers.CompositeRangeBuilder;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.ClassNameTimeSeriesSerializer;
import com.emc.storageos.db.client.impl.ClassNameTimeSeriesIndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.constraint.TimeSeriesConstraint;

public class ClassNameTimeSeriesConstraintImpl extends ConstraintImpl<ClassNameTimeSeriesIndexColumnName>
        implements TimeSeriesConstraint {
    private static final Logger log = LoggerFactory.getLogger(ClassNameTimeSeriesConstraintImpl.class);

    private final ColumnFamily<String, ClassNameTimeSeriesIndexColumnName> _altIdCf;
    private final String _altId;
    private final Class<? extends DataObject> _entryType;
    private long startTimeMicros;
    private long endTimeMicros;
    private long lastMatchedTimeStamp = 0;

    private Keyspace _keyspace;

    public ClassNameTimeSeriesConstraintImpl(ColumnField field, String altId, long startTimeInMS, long endTimeInMS) {
        super(field, altId);
        indexSerializer = ClassNameTimeSeriesSerializer.get();

        _altIdCf = field.getIndexCF();
        _altId = altId;
        _entryType = field.getDataObjectType();
        startTimeMicros = startTimeInMS*1000L;
        endTimeMicros = endTimeInMS * 1000L;
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        queryOnePageWithAutoPaginate(genQuery(),result);
    }

    public long getLastMatchedTimeStamp() {
        return lastMatchedTimeStamp;
    }

    @Override
    protected RowQuery<String, ClassNameTimeSeriesIndexColumnName> genQuery() {
        return genQuery(genRangeBuilder().limit(pageCount));
    }

    private RowQuery<String, ClassNameTimeSeriesIndexColumnName> genQuery(CompositeRangeBuilder builder) {
        return _keyspace.prepareQuery(_altIdCf).getKey(_altId).withColumnRange(builder);
    }

    private CompositeRangeBuilder genRangeBuilder() {
        return ClassNameTimeSeriesSerializer.get().buildRange()
                .withPrefix(_entryType.getSimpleName())
                .greaterThan(startTimeMicros)
                .lessThanEquals(endTimeMicros);
    }

    @Override
    public long count() throws ConnectionException {
        try {
            OperationResult<Integer> countResult = genQuery(genRangeBuilder()).getCount().execute();
            long count = countResult.getResult();
            return count;
        }catch (ConnectionException e) {
            log.error("Failed to get count e=", e);
            throw e;
        }
    }

    @Override
    protected URI getURI(Column<ClassNameTimeSeriesIndexColumnName> col) {
        return URI.create(col.getName().getThree());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<ClassNameTimeSeriesIndexColumnName> column) {
        lastMatchedTimeStamp = column.getName().getTimeInMicros()/1000;
        return result.createQueryHit(getURI(column));
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _entryType;
    }

    @Override
    public boolean isValid() {
        return _altId != null && !_altId.isEmpty();
    }
}

