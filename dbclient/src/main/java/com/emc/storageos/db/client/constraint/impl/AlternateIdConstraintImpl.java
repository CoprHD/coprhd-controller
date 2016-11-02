/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

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
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer;
import com.emc.storageos.db.client.model.AlternateId;
import com.netflix.astyanax.serializers.StringSerializer;



/**
 * Alternate ID constraint implementation
 */
public class AlternateIdConstraintImpl extends ConstraintImpl<IndexColumnName> implements AlternateIdConstraint {
    private static final Logger log = LoggerFactory.getLogger(AlternateIdConstraintImpl.class);

    private final ColumnFamily<String, IndexColumnName> _altIdCf;
    private final String _altId;
    private final Class<? extends DataObject> _entryType;
    /*
    private long startTimeMicros;
    private long endTimeMicros;
    */

    private Keyspace _keyspace;

    static int n = 0;

    /*
    public AlternateIdConstraintImpl(ColumnField field, String altId) {
        this(field, altId, 0, 0);
    }

    public AlternateIdConstraintImpl(ColumnField field, String altId, long sTimeInMicros, long eTimeInMicros) {
    */
    public AlternateIdConstraintImpl(ColumnField field, String altId) {
        super(field, altId);
        indexSerializer = IndexColumnNameSerializer.get();

        _altIdCf = field.getIndexCF();
        _altId = altId;
        _entryType = field.getDataObjectType();
        /*
        startTimeMicros = sTimeInMicros*1000L;
        endTimeMicros = eTimeInMicros* 1000L;
        */
    }

    public <T extends DataObject> AlternateIdConstraintImpl(String indexCFName, String altId, Class<T> entryType, long sTimeInMicros, long eTimeInMicros) {
        super(altId);
        indexSerializer = IndexColumnNameSerializer.get();

        _altIdCf = new ColumnFamily<String, IndexColumnName>(indexCFName, StringSerializer.get(),
                IndexColumnNameSerializer.get());
        _altId = altId;
        _entryType = entryType;
        /*
        startTimeMicros = sTimeInMicros*1000L;
        endTimeMicros = eTimeInMicros* 1000L;

        log.info("lbymm: startTime={} endTime={}", startTimeMicros, endTimeMicros);
        */
    }

    public String getAltId() {
        return _altId;
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        //queryOnePageWithoutAutoPaginate(genQuery(), _entryType.getSimpleName(), result);
        queryOnePageWithAutoPaginate(genQuery(), result);
    }

    @Override
    public RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query;
        if (startId == null) {
            query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                    .withColumnRange(CompositeColumnNameSerializer.get().buildRange()
                            .greaterThanEquals(_entryType.getSimpleName())
                            .lessThanEquals(_entryType.getSimpleName())
                            .limit(pageCount));
        }else {
            query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                    .withColumnRange(CompositeColumnNameSerializer.get().buildRange()
                            .withPrefix(_entryType.getSimpleName())
                            .greaterThan(startId) // match all column2
                            .lessThan("x") // match all column2
                            .limit(pageCount));

        }
        return query;
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        return URI.create(col.getName().getTwo());
    }

    /*
    @Override
    protected <T> QueryHitIterator<T, IndexColumnName> getQueryHitIterator(RowQuery<String, IndexColumnName> query,
                                                          final QueryResult<T> result) {
        FilteredQueryHitIterator<T, IndexColumnName> it = new FilteredQueryHitIterator<T, IndexColumnName>(query) {
            @Override
            protected T createQueryHit(Column<IndexColumnName> column) {
                matchedCount++;
                if (reachMaxCount()) {
                    log.info("lbymm34 set stop to true");
                    stop = true;
                }
                // log.info("id3={} matchedCount={} stack=", getURI(column), matchedCount, new Throwable());
                return result.createQueryHit(getURI(column));
            }

            @Override
            public boolean filter(Column<IndexColumnName> column) {
                long timeMarked = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());
                */

                /*
                log.info("lby1 starTime={}", startTimeMicros);
                log.info("     endTime= {}", endTimeMicros);
                log.info("    marktime= {}", timeMarked);
                */

                // n++;
                // log.info("lbyt n={} reachMaxCount={} stack=", n, reachMaxCount(), new Throwable());
                /*
                if (reachMaxCount()) {
                    log.info("lbymm34 set stop to true");
                    stop = true;
                }
                */

                //boolean ret = (!reachMaxCount()) && ((startTimeMicros <= 0 && endTimeMicros <= 0) || isInTimeRange(timeMarked));
    /*
                boolean ret = (!stop) && ((startTimeMicros <= 0 && endTimeMicros <= 0) || isInTimeRange(timeMarked));
                // log.info("lbyt1 n={} ret={} inRange={}", n, ret, isInTimeRange(timeMarked));
                return ret;
            }
        };

        return it;
    }
    */

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        /*
        if (startTimeMicros != 0 && endTimeMicros != 0) {
            long timeMarked = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID());
            if (!isInTimeRange(timeMarked)) {
                log.info("Not in range result={} stack=", result, new Throwable());
                return null;
            }
        }
        */

        return result.createQueryHit(getURI(column));
    }

    /*
    private boolean isInTimeRange(long timeMarked) {
        return (startTimeMicros >= 0) && (endTimeMicros > 0)
                &&  (startTimeMicros < timeMarked) && (timeMarked < endTimeMicros);
    }
    */

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _entryType;
    }

	@Override
	public boolean isValid() {
        return this._altId!=null && !this._altId.isEmpty();
	}

    public void migrate(String cfName) {

    }
}
