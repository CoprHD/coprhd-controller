/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.RelationDbIndex;
import com.emc.storageos.db.client.model.DataObject;

/**
 * A containment constraint that returns only those elements from the index that were added between startTime and endTime
 */
public class TimedContainmentConstraintImpl extends ConstraintImpl {
	private static final Logger log = LoggerFactory.getLogger(TimedContainmentConstraintImpl.class);
    private static final long MILLIS_TO_MICROS = 1000L;
    private final long startTimeMicros;
    private final long endTimeMicros;

    private URI indexKey;
    private Class<? extends DataObject> entryType;
    private final ColumnField field;

    public TimedContainmentConstraintImpl(URI indexKey, long startTimeMicros, long endTimeMicros, Class<? extends DataObject> entryType,
            ColumnField field) {
        super(indexKey, entryType, field);
        this.startTimeMicros = startTimeMicros * MILLIS_TO_MICROS;
        this.endTimeMicros = endTimeMicros * MILLIS_TO_MICROS;

        this.indexKey = indexKey;
        this.entryType = entryType;
        this.field = field;
        returnOnePage = true;
        cf = field.getIndexCF().getName();
    }

    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        QueryHitIterator<T> it = createQueryHitIterator(genQueryStatement(), result);
        it.prime();
        result.setResult(it);
    }
    
    @Override
    protected Statement genQueryStatement() {
        String queryString = String.format("select * from \"%s\" where key=? and column1=?", cf);
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString);
        Statement statement =  preparedStatement.bind(indexKey.toString(),
                entryType.getSimpleName());
        statement.setFetchSize(pageCount);
        
        log.info("query string: {}", preparedStatement.getQueryString());
        return statement;
    }

    protected <T> QueryHitIterator<T> createQueryHitIterator(Statement statement, final QueryResult<T> result) {

        return new FilteredQueryHitIterator<T>(dbClientContext, statement) {
            @Override
            protected T createQueryHit(IndexColumnName column) {
                return result.createQueryHit(getURI(column), column.getThree(), column.getTimeUUID());
            }

            @Override
            public boolean filter(IndexColumnName column) {
                long columnTime = UUIDs.unixTimestamp(column.getTimeUUID())*1000;
                // Filtering on startTime, startTime = -1 for no filtering
                if (startTimeMicros > 0 && columnTime < startTimeMicros) {
                    return false;
                }
                // Filtering on endTime, endTime = -1 for no filtering
                if (endTimeMicros > 0 && columnTime > endTimeMicros) {
                    return false;
                }
                return true;
            }
        };
    }

    @Override
    protected <T> T createQueryHit(QueryResult<T> result, IndexColumnName column) {
        return result.createQueryHit(getURI(column), column.getThree(), column.getTimeUUID());
    }

    @Override
    protected URI getURI(IndexColumnName col) {
        URI ret;
        if (field.getIndex() instanceof RelationDbIndex) {
            ret = URI.create(col.getTwo());
        } else {
            ret = URI.create(col.getFour());
        }

        return ret;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return field.getDataObjectType();
    }
    
	@Override
	public boolean isValid() {
        return this.indexKey!=null && !this.indexKey.toString().isEmpty();
	}
}
