package com.emc.storageos.db.client.constraint.impl;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
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

    @Override
    protected RowQuery<String, IndexColumnName2> genQuery() {
        log.info("lbyx: cf={} key={}", _altIdCf.getName(), _altId);
        log.info("prefix={} startime={} endtime={}", _entryType.getSimpleName(), startTimeMicros, endTimeMicros);
        log.info("pageCount={}", pageCount);
        /*
        try {
            log.info("lbyuu");
            OperationResult<Integer> count = _keyspace.prepareQuery(_altIdCf).getKey(_altId).getCount().execute();
            log.info("lbyuu count={}", count.getResult());
        }catch (ConnectionException e) {
            log.info("lbyuu");
        }
        */

        return _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                .withColumnRange(IndexColumnNameSerializer2.get().buildRange()
                        .withPrefix(_entryType.getSimpleName())
                        .greaterThan(startTimeMicros)
                        .lessThanEquals(endTimeMicros)
                        .limit(pageCount));
    }

    @Override
    protected URI getURI(Column<IndexColumnName2> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName2> column) {
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

