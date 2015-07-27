/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.constraint.impl;

import java.util.NoSuchElementException;

import com.emc.storageos.db.client.impl.IndexColumnName;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.query.RowQuery;

/**
 *  QueryHitIterator with a filter based on column name
 */
public abstract class FilteredQueryHitIterator<T> extends QueryHitIterator<T> {
    private Column<IndexColumnName> _current; 
    
    public FilteredQueryHitIterator(RowQuery<String, IndexColumnName> query) {
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
        while(_currentIt != null) {
            skipToNext();
            if (_current != null) {
                return;
            }
            super.runQuery();
        }
    }

    @Override
    public boolean hasNext() {
        return (_current != null);
    }

    @Override
    public T next() {
        if (_current == null) {
            throw new NoSuchElementException();
        }
        T ret = createQueryHit(_current);
        moveNext();
        return ret;
    }

    /**
     * check if a particular column is good or not
     * @param column
     * @return true if filter likes column, false otherwise
     */
    public abstract boolean filter(Column<IndexColumnName> column);
}
