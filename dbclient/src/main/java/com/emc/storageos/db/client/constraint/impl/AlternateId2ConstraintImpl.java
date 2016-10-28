package com.emc.storageos.db.client.constraint.impl;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.IndexColumnName2;
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer;
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer2;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.util.TimeUUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;

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

    static int n = 0;

    public AlternateId2ConstraintImpl(ColumnField field, String altId) {
        this(field, altId, 0, 0);
    }

    public AlternateId2ConstraintImpl(ColumnField field, String altId, long sTimeInMicros, long eTimeInMicros) {
        super(field, altId);
        indexSerializer = IndexColumnNameSerializer2.get();

        _altIdCf = field.getIndexCF();
        _altId = altId;
        _entryType = field.getDataObjectType();
        startTimeMicros = sTimeInMicros*1000L;
        endTimeMicros = eTimeInMicros* 1000L;

        log.info("lbyn: startTime={} endTime={}", startTimeMicros, endTimeMicros);
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
    protected RowQuery<String, IndexColumnName2> genQuery() {
        RowQuery<String, IndexColumnName2> query;
        UUID start, end;

        if (startId == null) {
            start = TimeUUIDUtils.getMicrosTimeUUID(0);
            long nowInMacros = System.nanoTime() / 1000;
            end = TimeUUIDUtils.getMicrosTimeUUID(nowInMacros);
        }else {
            start = TimeUUIDUtils.getMicrosTimeUUID(startTimeMicros);
            end = TimeUUIDUtils.getMicrosTimeUUID(endTimeMicros);
        }

        return _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                    .withColumnRange(CompositeColumnNameSerializer.get().buildRange()
                            .greaterThan(start)
                            .lessThan(end)
                            .limit(pageCount));
    }

    @Override
    protected URI getURI(Column<IndexColumnName2> col) {
        return URI.create(col.getName().getThree());
    }

    @Override
    protected <T> QueryHitIterator<T, IndexColumnName2>
       //getQueryHitIterator(RowQuery<String, IndexColumnName> query,
       getQueryHitIterator(RowQuery<String, IndexColumnName2> query,
                                         final QueryResult<T> result) {
        FilteredQueryHitIterator<T, IndexColumnName2> it =
                new FilteredQueryHitIterator<T, IndexColumnName2>(query) {
            @Override
            protected T createQueryHit(Column<IndexColumnName2> column) {
                matchedCount++;
                // log.info("lbymm32 matchedCount={} stack=", matchedCount, new Throwable());

                if (reachMaxCount()) {
                    log.info("lbymm34 set stop to true");
                    stop = true;
                }
                // log.info("id3={} matchedCount={} stack=", getURI(column), matchedCount, new Throwable());
                return result.createQueryHit(getURI(column));
            }

            @Override
            public boolean filter(Column<IndexColumnName2> column) {
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
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName2> column) {
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

