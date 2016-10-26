/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Date;

import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.netflix.astyanax.serializers.AnnotatedCompositeSerializer;
import com.netflix.astyanax.serializers.CompositeRangeBuilder;
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

    static int n = 0;

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

        log.info("lbymm: startTime={} endTime={}", startTimeMicros, endTimeMicros);
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        //queryOnePageWithoutAutoPaginate(genQuery(), _entryType.getSimpleName(), result);
        queryOnePageWithAutoPaginate(genQuery(),result);
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        /*
        AnnotatedCompositeSerializer<CompositeColumnName> range = CompositeColumnNameSerializer.get();
        CompositeRangeBuilder builder = range.buildRange();
        builder.withPrefix(_entryType.getSimpleName())
               //.lessThanEquals(_entryType.getSimpleName())
                .limit(pageCount);

        try {
            Method m = builder.getClass().getDeclaredMethod("nextComponent", null);
            m.setAccessible(true);
            m.invoke(builder);
            builder.greaterThan("a");
            builder.lessThan("x");
        }catch(NoSuchMethodException |IllegalAccessException | InvocationTargetException e) {
            log.error("lbyxx e=",e);
        }
        */

        /*
        RowQuery<String, IndexColumnName> query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                .withColumnRange(builder);
                */
        RowQuery<String, IndexColumnName> query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                .withColumnRange(CompositeColumnNameSerializer.get().buildRange()
                        .greaterThanEquals(_entryType.getSimpleName())
                        .lessThanEquals(_entryType.getSimpleName())
                        // .greaterThan("a") // match all column2
                        // .lessThan("x") // match all column2
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
                matchedCount++;
                // log.info("lbymm32 matchedCount={} stack=", matchedCount, new Throwable());

                if (reachMaxCount()) {
                    log.info("lbymm34 set stop to true");
                    stop = true;
                }
                return result.createQueryHit(getURI(column));
            }

            @Override
            public boolean filter(Column<IndexColumnName> column) {
                long timeMarked = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());

                log.info("lby1 starTime={}", startTimeMicros);
                log.info("     endTime= {}", endTimeMicros);
                log.info("    marktime= {}", timeMarked);

                // n++;
                // log.info("lbyt n={} reachMaxCount={} stack=", n, reachMaxCount(), new Throwable());
                if (reachMaxCount()) {
                    log.info("lbymm34 set stop to true");
                    stop = true;
                }

                //boolean ret = (!reachMaxCount()) && ((startTimeMicros <= 0 && endTimeMicros <= 0) || isInTimeRange(timeMarked));
                boolean ret = (!stop) && ((startTimeMicros <= 0 && endTimeMicros <= 0) || isInTimeRange(timeMarked));
                // log.info("lbyt1 n={} ret={} inRange={}", n, ret, isInTimeRange(timeMarked));
                return ret;
            }
        };

        return it;
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        //log.info("lbymm1 starTime={} endTime={} stack=", startTimeMicros, endTimeMicros, new Throwable());
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
