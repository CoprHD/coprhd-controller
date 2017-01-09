/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.query.RowQuery;

import com.emc.storageos.db.client.impl.CompositeIndexColumnName;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * QueryHit iterator
 */
public abstract class QueryHitIterator<T1, T2 extends CompositeIndexColumnName>
        implements Iterator<T1> {
    protected RowQuery<String, T2> _query;
    protected Iterator<Column<T2>> _currentIt;

    public QueryHitIterator(RowQuery<String, T2> query) {
        _query = query;
    }

    public void prime() {
        runQuery();
    }

    protected void runQuery() {
        _currentIt = null;

        ColumnList<T2> result;

        try {
            result = _query.execute().getResult();
        } catch (final ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }

        if (!result.isEmpty()) {
            _currentIt = result.iterator();
        }
    }

    @Override
    public boolean hasNext() {
        if (_currentIt == null) {
            return false;
        }

        if (_currentIt.hasNext()) {
            return true;
        }

        runQuery();
        return _currentIt != null;
    }

    @Override
    public T1 next() {
        if (_currentIt == null) {
            throw new NoSuchElementException();
        }
        return createQueryHit(_currentIt.next());
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    protected abstract T1 createQueryHit(Column<T2> column);
}
