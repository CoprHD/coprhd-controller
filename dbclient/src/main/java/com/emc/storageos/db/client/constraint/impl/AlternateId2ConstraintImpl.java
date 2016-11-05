package com.emc.storageos.db.client.constraint.impl;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.query.RowQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName2;
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer2;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Alternate ID constraint implementation
 */
public class AlternateId2ConstraintImpl extends ConstraintImpl<IndexColumnName2> implements AlternateIdConstraint {
    private static final Logger log = LoggerFactory.getLogger(AlternateId2ConstraintImpl.class);

    private final ColumnFamily<String, IndexColumnName2> _altIdCf;
    private final String _altId;
    private final Class<? extends DataObject> _entryType;
    private long startTimeMicros;
    private long endTimeMicros;
    private long lastMatchedTimeStamp = 0;

    private Keyspace _keyspace;

    public AlternateId2ConstraintImpl(ColumnField field, String altId, long startTimeInMS, long endTimeInMS) {
        super(field, altId);
        indexSerializer = IndexColumnNameSerializer2.get();

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
    protected RowQuery<String, IndexColumnName2> genQuery() {
        log.info("cf={} key={}", _altIdCf.getName(), _altId);
        log.info("prefix={} startime= {} endtime= {}", _entryType.getSimpleName(), startTimeMicros, endTimeMicros);
        log.info("pageCount={}", pageCount);

        RowQuery<String, IndexColumnName2> query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                .withColumnRange(IndexColumnNameSerializer2.get().buildRange()
                        .withPrefix(_entryType.getSimpleName())
                        .greaterThan(startTimeMicros)
                        .lessThanEquals(endTimeMicros)
                        .limit(pageCount));

        return query;
    }

    public int count() throws ConnectionException {
        try {
            OperationResult<Integer> countResult = _keyspace.prepareQuery(_altIdCf).getKey(_altId).getCount().execute();
            int count = countResult.getResult();
            return count;
        }catch (ConnectionException e) {
            log.error("Failed to get count e=", e);
            throw e;
        }
    }

    @Override
    protected URI getURI(Column<IndexColumnName2> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName2> column) {
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

