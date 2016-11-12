package com.emc.storageos.db.client.constraint.impl;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.query.RowQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Date;

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
    private boolean markForDelete = false;
    private MutationBatch mutationBatch;
    private long matchedCount = 0;

    private Keyspace _keyspace;

    public AlternateId2ConstraintImpl(ColumnField field, String altId, long startTimeInMS, long endTimeInMS, boolean markForDelete) {
        super(field, altId);
        indexSerializer = IndexColumnNameSerializer2.get();

        _altIdCf = field.getIndexCF();
        _altId = altId;
        _entryType = field.getDataObjectType();
        startTimeMicros = startTimeInMS*1000L;
        endTimeMicros = endTimeInMS * 1000L;
        this.markForDelete = markForDelete;
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
        log.info("cf={} key={} delete={}", _altIdCf.getName(), _altId, markForDelete);
        log.info("prefix={} startime= {} endtime= {}", _entryType.getSimpleName(), startTimeMicros, endTimeMicros);
        log.info("pageCount={} false={}", pageCount, Boolean.FALSE.toString());
        try {
            log.info("keyspace={}", _keyspace.describeKeyspace().getName());
        }catch (Exception e) {
            log.error("lbymm9 e=",e);
        }

        RowQuery<String, IndexColumnName2> query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                .withColumnRange(IndexColumnNameSerializer2.get().buildRange()
                        .withPrefix(_entryType.getSimpleName())
                        .withPrefix(Boolean.FALSE.toString())
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
        //log.info("lbymm0 markForDelete={} stack=", markForDelete, new Throwable());
        log.info("lbymm0 markForDelete={} ", markForDelete);
        if (markForDelete) {
            matchedCount++;

            log.info("lbymm1");
            if (markForDelete) {
                mutationBatch = _keyspace.prepareMutationBatch();
            }

            IndexColumnName2 columnName = column.getName();
            IndexColumnName2 col = new IndexColumnName2(columnName.getOne(), columnName.getTwo(), true, columnName.getTimeInMicros());
            mutationBatch.withRow(_altIdCf, _altId).deleteColumn(columnName);
            mutationBatch.withRow(_altIdCf, _altId).putEmptyColumn(col, null);

            //if ( matchedCount % pageCount == 0) {
                try {
                    log.info("lbymm2");
                    mutationBatch.execute(); // commit
                    log.info("lbymm3");
                }catch (ConnectionException e) {
                    log.error("Failed to update key={} column={} e=", _altId, columnName, e);
                    throw new RuntimeException(e);
                }
                mutationBatch = _keyspace.prepareMutationBatch();
            log.info("lbymm4");
            // }
        }
        log.info("lbydd0");
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

