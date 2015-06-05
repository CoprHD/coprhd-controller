/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.constraint.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.query.RowQuery;

/**
 * QueryHit iterator
 */
public abstract class QueryHitIterator<T> implements Iterator<T> {
    protected RowQuery<String, IndexColumnName> _query;
    protected Iterator<Column<IndexColumnName>> _currentIt;

    public QueryHitIterator(RowQuery<String, IndexColumnName> query) {
        _query = query;
    }

    public void prime() {
        runQuery();
    }

    protected void runQuery() {
        _currentIt = null;
        ColumnList<IndexColumnName> result;
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
    public T next() {
        if (_currentIt == null) {
            throw new NoSuchElementException();
        }
        return createQueryHit(_currentIt.next());
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    protected abstract T createQueryHit(Column<IndexColumnName> column);
}
