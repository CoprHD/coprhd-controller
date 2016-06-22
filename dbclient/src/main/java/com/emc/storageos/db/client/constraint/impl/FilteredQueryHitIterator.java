/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.util.NoSuchElementException;

import com.datastax.driver.core.Statement;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.IndexColumnName;

/**
 * QueryHitIterator with a filter based on column name
 */
public abstract class FilteredQueryHitIterator<T> extends QueryHitIterator<T> {
    private IndexColumnName _current;
    
    public FilteredQueryHitIterator(DbClientContext dbClientContext, Statement queryStatement) {
        super(dbClientContext, queryStatement);
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
        while (resultIterator.hasNext()) {
            _current = toIndexColumnName(resultIterator.next());
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
        skipToNext();
        if (_current != null) {
        	return;
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
     * 
     * @param column
     * @return true if filter likes column, false otherwise
     */
    public abstract boolean filter(IndexColumnName column);
}
