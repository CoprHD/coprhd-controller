/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.ConnectionException;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.query.RowQuery;

/**
 * QueryHit iterator
 */
public abstract class QueryHitIterator<T> implements Iterator<T> {
    protected RowQuery<String, IndexColumnName> _query;
    protected Statement queryStatement;
    protected ResultSet resultSet;
    protected DbClientContext dbClientContext;

    public QueryHitIterator(RowQuery<String, IndexColumnName> query) {
        _query = query;
    }
    
    public QueryHitIterator(DbClientContext dbClientContext, Statement queryStatement) {
        this.queryStatement = queryStatement;
        this.dbClientContext = dbClientContext;
    }

    public void prime() {
        runQuery();
    }

    protected void runQuery() {
        try {
            resultSet = dbClientContext.getSession().execute(queryStatement);
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    @Override
    public boolean hasNext() {
        return !resultSet.isExhausted();
    }

    @Override
    public T next() {
        if (resultSet.isExhausted()) {
            throw new NoSuchElementException();
        }
        return createQueryHit(_currentIt.next());
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    protected abstract T createQueryHit(Column<IndexColumnName> column);
    
    protected T createQueryHit(IndexColumnName column) {
        return null;
    }
}
