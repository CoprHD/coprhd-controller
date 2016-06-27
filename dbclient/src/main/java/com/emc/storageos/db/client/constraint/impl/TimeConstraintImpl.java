/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.model.ColumnFamily;

/**
 * Constraint to query indexed columns on a start/end time. This uses the same
 * index as DecomissionedConstraintImpl but is designed to work on other column
 * families. This constraint takes a Start and End time and returns records
 * between this time period.
 */
public class TimeConstraintImpl extends ConstraintImpl implements DecommissionedConstraint {
	private static final Logger log = LoggerFactory.getLogger(TimeConstraintImpl.class);
    private static final long MILLIS_TO_MICROS = 1000L;
    private static final int DEFAULT_PAGE_SIZE = 100;
    private final ColumnFamily<String, IndexColumnName> cf;
    private final String rowKey;
    private final long startTimeMicros;
    private final long endTimeMicros;
    private Boolean value = null;
    private Class<? extends DataObject> entityType;

    /**
     * Constructs the time constraint.
     * 
     * @param clazz DataObject class
     * @param cf Column Family
     * @param startTimeMillis Start time in milliseconds or -1 for no filtering on start time.
     * @param endTimeMillis End time in milliseconds or -1 for no filtering on end time.
     */
    public TimeConstraintImpl(Class<? extends DataObject> clazz, ColumnFamily<String, IndexColumnName> cf,
            Boolean value, long startTimeMillis, long endTimeMillis) {
        this.cf = cf;
        rowKey = clazz.getSimpleName();
        this.startTimeMicros = startTimeMillis * MILLIS_TO_MICROS;
        this.endTimeMicros = endTimeMillis * MILLIS_TO_MICROS;
        this.value = value;
        this.entityType = clazz;
    }

    /**
     * Constructs the time constraint.
     * 
     * @param clazz DataObject class
     * @param cf Column Family
     * @param startTime Start time Date or null for no filtering on start time
     * @param endTime End time Date or null for no filtering on end time
     */
    public TimeConstraintImpl(Class<? extends DataObject> clazz, Boolean value,
            ColumnFamily<String, IndexColumnName> cf, Date startTime, Date endTime) {
        this(clazz, cf, value,
                startTime == null ? -1 : startTime.getTime(),
                endTime == null ? -1 : endTime.getTime());
    }
    
    @Override
    public <T> void execute(final QueryResult<T> result) {
        FilteredQueryHitIterator<T> it = new FilteredQueryHitIterator<T>(dbClientContext, genQueryStatement()) {
            @Override
            protected T createQueryHit(IndexColumnName column) {
                return result.createQueryHit(URI.create(column.getTwo()));
            }

            @Override
            public boolean filter(IndexColumnName column) {
                long timeMarked = UUIDs.unixTimestamp(column.getTimeUUID())*1000;
                // Filtering on startTime, startTime = -1 for no filtering
                if (startTimeMicros > 0 && timeMarked < startTimeMicros) {
                    return false;
                }
                // Filtering on endTime, endTime = -1 for no filtering
                if (endTimeMicros > 0 && timeMarked > endTimeMicros) {
                    return false;
                }
                return true;
            }
        };

        it.prime();
        result.setResult(it);
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        StringBuilder queryString = new StringBuilder();
        queryString.append("select").append(" * from \"").append(cf.getName()).append("\"");
        queryString.append(" where key=?");
        
        List<Object> queryParameters = new ArrayList<Object>();
        queryParameters.add(rowKey);
        
        queryOnePageWithoutAutoPaginate(queryString, Boolean.toString(value), result, queryParameters);
    }

    @Override
    protected URI getURI(IndexColumnName col) {
        return URI.create(col.getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, IndexColumnName column) {
        return result.createQueryHit(URI.create(column.getTwo()));
    }
    
    @Override
    protected Statement genQueryStatement() {
        StringBuilder queryString = new StringBuilder();
        queryString.append("select").append(" * from \"").append(cf.getName()).append("\"");
        queryString.append(" where key=?");
        
        List<Object> queryParameters = new ArrayList<Object>();
        queryParameters.add(rowKey);
        
        if (value != null) {
            queryString.append(" and column1=?");
            queryParameters.add(value.toString());
        }
        
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString.toString());
        Statement statement =  preparedStatement.bind(queryParameters.toArray(new Object[]{0}));
        statement.setFetchSize(DEFAULT_PAGE_SIZE);
        
        log.info("query string: {}", preparedStatement.getQueryString());
        return statement;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return entityType;
    }
    
	@Override
	public boolean isValid() {
        return rowKey!=null && !rowKey.isEmpty();
	}
}
