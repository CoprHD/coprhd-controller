/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.util.NoSuchElementException;

import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.query.RowQuery;

import com.emc.storageos.db.client.impl.CompositeIndexColumnName;

/**
 * QueryHitIterator with a filter based on column name
 */
public abstract class FilteredQueryHitIterator<T1, T2 extends CompositeIndexColumnName>
        extends QueryHitIterator<T1, T2> {
    private Column<T2> _current;

    public FilteredQueryHitIterator(RowQuery<String, T2> query) {
        super(query);
    }

    @Override
    protected void runQuery() {
        super.runQuery();
        moveNext();
    }

    /**
     * move current to the next valid entry per filter
     */
    private void skipToNext() {
        while (_currentIt.hasNext()) {
            _current = _currentIt.next();
            if (filter(_current)) {
                break;
            } else {
                _current = null;
            }
        }
    }

    /**
     * move current forward to next match
     */
    private void moveNext() {
        _current = null;
        while (_currentIt != null) {
            skipToNext();

            if (_current != null) {
                return;
            }

            super.runQuery();
        }
    }

    @Override
    public boolean hasNext() {
        return _current != null;
    }

    @Override
    public T1 next() {
        if (_current == null) {
            throw new NoSuchElementException();
        }
        T1 ret = createQueryHit(_current);
        moveNext();
        return ret;
    }

    /**
     * check if a particular column is good or not
     * 
     * @param column
     * @return true if filter likes column, false otherwise
     */
    public abstract boolean filter(Column<T2> column);
}
