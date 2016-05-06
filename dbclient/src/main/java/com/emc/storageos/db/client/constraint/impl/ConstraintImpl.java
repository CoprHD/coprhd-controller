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

import com.emc.storageos.db.client.constraint.ConstraintDescriptor;
import com.emc.storageos.db.exceptions.DatabaseException;

import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.impl.*;

/**
 * Abstract base for all containment queries
 */
public abstract class ConstraintImpl implements Constraint {
    private static final Logger log = LoggerFactory.getLogger(ConstraintImpl.class);
    private static final int DEFAULT_PAGE_SIZE = 100;

    private ConstraintDescriptor constraintDescriptor;

    protected String startId;
    protected int pageCount = DEFAULT_PAGE_SIZE;
    protected boolean returnOnePage;

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

        this.returnOnePage = true;
    }

    public void setPageCount(int pageCount) {
        if (pageCount > 0) {
            this.pageCount = pageCount;
        }
    }

    @Override
    public <T> void execute(final Constraint.QueryResult<T> result) {
        try {
            if (returnOnePage) {
                queryOnePage(result);
                return;
            }
        } catch (ConnectionException e) {
            log.info("Query failed e=", e);
            throw DatabaseException.retryables.connectionFailed(e);
        }

        queryWithAutoPaginate(genQuery(), result, this);
    }

    protected abstract <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException;

    protected abstract RowQuery<String, IndexColumnName> genQuery();

    protected <T> void queryWithAutoPaginate(RowQuery<String, IndexColumnName> query, final QueryResult<T> result,
            final ConstraintImpl constraint) {
        query.autoPaginate(true);
        QueryHitIterator<T> it = new QueryHitIterator<T>(query) {
            @Override
            protected T createQueryHit(Column<IndexColumnName> column) {
                return constraint.createQueryHit(result, column);
            }
        };
        it.prime();
        result.setResult(it);
    }

    protected abstract URI getURI(Column<IndexColumnName> col);

    protected abstract <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> col);

    protected <T> void queryOnePageWithoutAutoPaginate(RowQuery<String, IndexColumnName> query, String prefix, final QueryResult<T> result)
            throws ConnectionException {

        CompositeRangeBuilder builder = IndexColumnNameSerializer.get().buildRange()
                .greaterThanEquals(prefix)
                .lessThanEquals(prefix)
                .reverse() // last column comes only
                .limit(1);

        query.withColumnRange(builder);

        ColumnList<IndexColumnName> columns = query.execute().getResult();

        List<T> ids = new ArrayList();
        if (columns.isEmpty()) {
            result.setResult(ids.iterator());
            return; // not found
        }

        Column<IndexColumnName> lastColumn = columns.getColumnByIndex(0);

        String endId = lastColumn.getName().getTwo();

        builder = IndexColumnNameSerializer.get().buildRange();

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

        for (Column<IndexColumnName> col : columns) {
            T obj = createQueryHit(result, col);
            if (!ids.contains(obj)) {
                ids.add(createQueryHit(result, col));
            }
        }

        result.setResult(ids.iterator());
    }

    protected <T> void queryOnePageWithAutoPaginate(RowQuery<String, IndexColumnName> query, String prefix, final QueryResult<T> result)
            throws ConnectionException {
        CompositeRangeBuilder range = IndexColumnNameSerializer.get().buildRange()
                .greaterThanEquals(prefix)
                .lessThanEquals(prefix)
                .limit(pageCount);
        query.withColumnRange(range);

        queryOnePageWithAutoPaginate(query, result);
    }

    protected <T> void queryOnePageWithAutoPaginate(RowQuery<String, IndexColumnName> query, final QueryResult<T> result)
            throws ConnectionException {
        boolean start = false;
        List<T> ids = new ArrayList();
        int count = 0;

        query.autoPaginate(true);

        ColumnList<IndexColumnName> columns;

        while (count < pageCount) {
            columns = query.execute().getResult();

            if (columns.isEmpty())
            {
                break; // reach the end
            }

            for (Column<IndexColumnName> col : columns) {
                if (startId == null) {
                    start = true;
                } else if (startId.equals(getURI(col).toString())) {
                    start = true;
                    continue;
                }

                if (start) {
                    T obj = createQueryHit(result, col);
                    if (!ids.contains(obj)) {
                        ids.add(obj);
                    }
                    count++;
                }
            }
        }
        result.setResult(ids.iterator());
    }
}
