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
public class AlternateId3ConstraintImpl extends ConstraintImpl<IndexColumnName3> implements AlternateIdConstraint {
    private static final Logger log = LoggerFactory.getLogger(AlternateId3ConstraintImpl.class);

    private final ColumnFamily<String, IndexColumnName3> indexCF;
    private final Class<? extends DataObject> entryType;
    private long startTimeMicros;
    private long endTimeMicros;
    private long lastMatchedTimeStamp = 0;

    private Keyspace _keyspace;

    public AlternateId3ConstraintImpl(ColumnField field, long startTimeInMS, long endTimeInMS) {
        super(field);
        indexSerializer = IndexColumnNameSerializer3.get();

        indexCF = field.getIndexCF();
        entryType = field.getDataObjectType();
        startTimeMicros = startTimeInMS*1000L;
        endTimeMicros = endTimeInMS * 1000L;
        log.info("lbym1: key={} stack=", entryType.getSimpleName(), new Throwable());
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
    protected RowQuery<String, IndexColumnName3> genQuery() {
        log.info("cf={} key={}", indexCF.getName(), entryType.getSimpleName());
        log.info("startime= {} endtime= {}", startTimeMicros, endTimeMicros);
        log.info("pageCount={}", pageCount);
        try {
            log.info("keyspace={}", _keyspace.describeKeyspace().getName());
        }catch (Exception e) {
            log.error("lbymm9 e=",e);
        }

        RowQuery<String, IndexColumnName3> query = _keyspace.prepareQuery(indexCF).getKey(entryType.getSimpleName())
                .withColumnRange(IndexColumnNameSerializer3.get().buildRange()
                        .greaterThan(startTimeMicros)
                        .lessThanEquals(endTimeMicros)
                        .limit(pageCount));

        return query;
    }

    public int count() throws ConnectionException {
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
    protected URI getURI(Column<IndexColumnName3> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName3> column) {
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

