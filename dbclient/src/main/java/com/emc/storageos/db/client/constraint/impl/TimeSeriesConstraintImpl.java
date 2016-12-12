package com.emc.storageos.db.client.constraint.impl;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Alternate ID constraint implementation
 */
public class TimeSeriesConstraintImpl extends ConstraintImpl<TimeSeriesIndexColumnName> implements AlternateIdConstraint {
    private static final Logger log = LoggerFactory.getLogger(TimeSeriesConstraintImpl.class);

    private final ColumnFamily<String, TimeSeriesIndexColumnName> indexCF;
    private final Class<? extends DataObject> entryType;
    private final String indexKey;
    private long startTimeMicros;
    private long endTimeMicros;
    private long lastMatchedTimeStamp = 0;

    private Keyspace _keyspace;

    public TimeSeriesConstraintImpl(String indexKey, ColumnField field, long startTimeInMS, long endTimeInMS) {
        super(field);
        indexSerializer = TimeSeriesColumnNameSerializer.get();

        indexCF = field.getIndexCF();
        this.indexKey = indexKey;
        entryType = field.getDataObjectType();
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
    protected RowQuery<String, TimeSeriesIndexColumnName> genQuery() {
        log.info("cf={} key={} column1={}", indexCF.getName(), indexKey, entryType.getSimpleName());
        log.info("startime= {} endtime= {}", startTimeMicros, endTimeMicros);
        log.info("pageCount={}", pageCount);

        RowQuery<String, TimeSeriesIndexColumnName> query = _keyspace.prepareQuery(indexCF)
                //.getKey(entryType.getSimpleName())
                .getKey(indexKey)
                .withColumnRange(TimeSeriesColumnNameSerializer.get().buildRange()
                        .withPrefix(entryType.getSimpleName())
                        .greaterThan(startTimeMicros)
                        .lessThanEquals(endTimeMicros)
                        .limit(pageCount));

        return query;
    }

    public long count() throws ConnectionException {
        try {
            OperationResult<Integer> countResult = _keyspace.prepareQuery(indexCF).getKey(entryType.getSimpleName())
                    .getCount().execute();
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
        log.info("lbyg1");
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

