/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * QueryHit iterator
 */
public abstract class QueryHitIterator<T> implements Iterator<T> {
    protected Statement queryStatement;
    protected ResultSet resultSet;
    protected DbClientContext dbClientContext;
    protected Iterator<Row> resultIterator;
    
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
            resultIterator = resultSet.iterator();
        } catch (DriverException e) {
            throw DatabaseException.retryables.operationFailed(e);
        }
    }

    @Override
    public boolean hasNext() {
        return resultIterator.hasNext();
    }

    @Override
    public T next() {
        if (!resultIterator.hasNext()) {
            throw new NoSuchElementException();
        }
        return createQueryHit(toIndexColumnName(resultIterator.next()));
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
    
    protected abstract T createQueryHit(IndexColumnName column);
    
    protected IndexColumnName toIndexColumnName(Row row) {
        return new IndexColumnName(row.getString(1), 
                row.getString(2), 
                row.getString(3),
                row.getString(4),
                row.getUUID(5),
                row.getBytes(6));
    }
}
