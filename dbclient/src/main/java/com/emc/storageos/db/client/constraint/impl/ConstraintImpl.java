/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.serializers.CompositeRangeBuilder;
import com.netflix.astyanax.serializers.AnnotatedCompositeSerializer;

import com.emc.storageos.db.client.constraint.ConstraintDescriptor;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.impl.*;

/**
 * Abstract base for all containment queries
 */
public abstract class ConstraintImpl <T extends CompositeIndexColumnName> implements Constraint {
    private static final Logger log = LoggerFactory.getLogger(ConstraintImpl.class);
    private static final int DEFAULT_PAGE_SIZE = 100;

    private ConstraintDescriptor constraintDescriptor;

    protected String startId;
    protected int pageCount = DEFAULT_PAGE_SIZE;
    protected boolean returnOnePage;
    private int maxCount = -1;
    protected int matchedCount = 0;

    protected AnnotatedCompositeSerializer<T> indexSerializer;

    public ConstraintImpl(Object... arguments) {
        ColumnField field = null;
        int cfPosition = 0;
        int i = 0;

        List args = new ArrayList();
        for (Object argument : arguments) {
            i++;
            if (argument instanceof ColumnField) {
                field = (ColumnField) argument;
                cfPosition = i;
                continue;
            }

            args.add(argument);
        }

        // TODO: remove this once TimeConstraintImpl has been reworked to work over geo-queries
        if (this instanceof TimeConstraintImpl) {
            return;
        }

        if (field == null) {
            throw new IllegalArgumentException("ColumnField should be in the constructor arguments");
        }

        String dataObjClassName = field.getDataObjectType().getName();
        String fieldName = field.getName();

        constraintDescriptor = new ConstraintDescriptor();
        constraintDescriptor.setConstraintClassName(this.getClass().getName());
        constraintDescriptor.setDataObjectClassName(dataObjClassName);
        constraintDescriptor.setColumnFieldName(fieldName);
        constraintDescriptor.setColumnFieldPosition(cfPosition);
        constraintDescriptor.setArguments(args);
    }

    @Override
    public ConstraintDescriptor toConstraintDescriptor() {
        return constraintDescriptor;
    }

    public abstract boolean isValid();
    
    public void setStartId(URI startId) {
        if (startId != null) {
            this.startId = startId.toString();
        }

        // this.returnOnePage = true;
    }

    public void setPageCount(int pageCount) {
        if (pageCount > 0) {
            this.pageCount = pageCount;
        }
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    @Override
    public <T> void execute(final Constraint.QueryResult<T> result) {
        /*
        try {
            if (returnOnePage) {
                queryOnePage(result);
                return;
            }
        } catch (ConnectionException e) {
            log.info("Query failed e=", e);
            throw DatabaseException.retryables.connectionFailed(e);
        }
        */

        queryWithAutoPaginate(genQuery(), result);
    }

    protected abstract <T1> void queryOnePage(final QueryResult<T1> result) throws ConnectionException;

    //protected abstract RowQuery<String, IndexColumnName> genQuery();
    protected abstract RowQuery<String, T> genQuery();

    //protected <T1> void queryWithAutoPaginate(RowQuery<String, IndexColumnName> query, final QueryResult<T1> result) {
    protected <T1> void queryWithAutoPaginate(RowQuery<String, T> query, final QueryResult<T1> result) {
        query.autoPaginate(true);
        QueryHitIterator<T1, T> it = getQueryHitIterator(query, result);
        it.prime();
        result.setResult(it);
    }

    protected <T3> QueryHitIterator<T3, T> getQueryHitIterator(RowQuery<String, T> query,
                                                          final QueryResult<T3> result) {
        final ConstraintImpl constraint = this;
        QueryHitIterator<T3, T> it = new QueryHitIterator<T3, T>(query) {
            @Override
            protected T3 createQueryHit(Column<T> column) {
                return (T3)constraint.createQueryHit(result, column);
            }
        };

        return it;
    }

    protected boolean reachMaxCount() {
        // log.info("lbymm30 matchedCount={} maxCount={}", matchedCount, maxCount);
        return (maxCount >0) && (matchedCount >= maxCount);
    }

    //protected abstract URI getURI(Column<IndexColumnName> col);
    protected abstract URI getURI(Column<T> col);

    protected abstract <T1> T1 createQueryHit(final QueryResult<T1> result, Column<T> col);

    //protected <T1> void queryOnePageWithoutAutoPaginate(RowQuery<String, IndexColumnName> query, String prefix, final QueryResult<T> result)
    protected <T1> void queryOnePageWithoutAutoPaginate(RowQuery<String, T> query, String prefix, final QueryResult<T1> result)
            throws ConnectionException {

        //CompositeRangeBuilder builder = IndexColumnNameSerializer.get().buildRange()
        CompositeRangeBuilder builder = indexSerializer.buildRange()
                .greaterThanEquals(prefix)
                .lessThanEquals(prefix)
                .reverse() // last column comes only
                .limit(1);

        query.withColumnRange(builder);

        //ColumnList<IndexColumnName> columns = query.execute().getResult();
        ColumnList<T> columns = query.execute().getResult();

        List<T1> ids = new ArrayList();
        if (columns.isEmpty()) {
            result.setResult(ids.iterator());
            return; // not found
        }

        //Column<IndexColumnName> lastColumn = columns.getColumnByIndex(0);
        Column<T> lastColumn = columns.getColumnByIndex(0);

        String endId = lastColumn.getName().getTwo();

        //builder = IndexColumnNameSerializer.get().buildRange();
        builder = indexSerializer.buildRange();

        if (startId == null) {
            // query first page
            builder.greaterThanEquals(prefix)
                    .lessThanEquals(prefix)
                    .limit(pageCount);

        } else {
            builder.withPrefix(prefix)
                    .greaterThan(startId)
                    .lessThanEquals(endId)
                    .limit(pageCount);
        }

        query = query.withColumnRange(builder);

        columns = query.execute().getResult();

        // for (Column<IndexColumnName> col : columns) {
        for (Column<T> col : columns) {
            if (reachMaxCount()) {
                break;
            }

            T1 obj = createQueryHit(result, col);
            if (obj != null && !ids.contains(obj)) {
                ids.add(createQueryHit(result, col));
                matchedCount++;
            }
        }

        // log.info("lbymm3: matched={}", matchedCount);
        result.setResult(ids.iterator());
    }

    //protected <T> void queryOnePageWithAutoPaginate(RowQuery<String, IndexColumnName> query, String prefix, final QueryResult<T> result)
    protected <T1> void queryOnePageWithAutoPaginate(RowQuery<String, T> query, String prefix, final QueryResult<T1> result)
            throws ConnectionException {
        //CompositeRangeBuilder range = IndexColumnNameSerializer.get().buildRange()
        CompositeRangeBuilder range = indexSerializer.buildRange()
                .greaterThanEquals(prefix)
                .lessThanEquals(prefix)
                .limit(pageCount);
        query.withColumnRange(range);

        queryOnePageWithAutoPaginate(query, result);
    }

    //protected <T> void queryOnePageWithAutoPaginate(RowQuery<String, IndexColumnName> query, final QueryResult<T> result)
    protected <T1> void queryOnePageWithAutoPaginate(RowQuery<String, T> query, final QueryResult<T1> result)
            throws ConnectionException {
        boolean start = false;
        List<T1> ids = new ArrayList();
        int count = 0;

        query.autoPaginate(true);

        //ColumnList<IndexColumnName> columns;
        ColumnList<T> columns;

        while ((count < pageCount) && !reachMaxCount()) {
            columns = query.execute().getResult();

            if (columns.isEmpty())
            {
                break; // reach the end
            }

            //for (Column<IndexColumnName> col : columns) {
            for (Column<T> col : columns) {
                if (startId == null) {
                    start = true;
                } else if (startId.equals(getURI(col).toString())) {
                    start = true;
                    continue;
                }

                if (start) {
                    T1 obj = createQueryHit(result, col);
                    if (obj != null && !ids.contains(obj)) {
                        ids.add(obj);
                        matchedCount++;
                        if (reachMaxCount()) {
                            break;
                        }
                    }
                    count++;
                }
            }
        }

        // log.info("lbymm31 matchedCount={}", matchedCount);
        result.setResult(ids.iterator());
    }
}
