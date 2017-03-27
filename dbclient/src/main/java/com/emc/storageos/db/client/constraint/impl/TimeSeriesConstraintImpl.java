/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.TimeSeriesConstraint;
import com.emc.storageos.db.client.impl.ColumnFamilyDefinition;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeIndexColumnName;
import com.emc.storageos.db.client.impl.TimeSeriesIndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

public class TimeSeriesConstraintImpl extends ConstraintImpl
        implements TimeSeriesConstraint {
    private static final Logger log = LoggerFactory.getLogger(TimeSeriesConstraintImpl.class);

    private final ColumnFamilyDefinition indexCF;
    private final Class<? extends DataObject> entryType;
    private final String indexKey;
    private long startTimeMicros;
    private long endTimeMicros;
    private long lastMatchedTimeStamp = 0;

    public TimeSeriesConstraintImpl(String indexKey, ColumnField field, long startTimeInMS, long endTimeInMS) {
        super(field);

        indexCF = field.getIndexCF();
        this.indexKey = indexKey;
        entryType = field.getDataObjectType();
        startTimeMicros = startTimeInMS*1000L;
        endTimeMicros = endTimeInMS * 1000L;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
    	queryOnePageWithAutoPaginate(genQueryStatement(), result);
    }

    public long getLastMatchedTimeStamp() {
        return lastMatchedTimeStamp;
    }
    
    @Override
    protected Statement genQueryStatement() {
    	String queryString = String.format("select * from \"%s\" where key='%s' and column1='%s' and column2>%s and column2<%s", 
    			indexCF, indexKey, entryType.getSimpleName(), startTimeMicros, endTimeMicros);
        
        SimpleStatement simpleStatement = new SimpleStatement(queryString);
        simpleStatement.setFetchSize(pageCount);
        
        log.debug("query string: {}", queryString);
        return simpleStatement;
    }

    @Override
    public long count() throws DriverException {
        try {
        	String queryString = String.format("select count(*) from \"%s\" where key='%s' and column1='%s' and column2>%s and column2<%s", 
        			indexCF, indexKey, entryType.getSimpleName(), startTimeMicros, endTimeMicros);
            
            SimpleStatement simpleStatement = new SimpleStatement(queryString);
            ResultSet result = dbClientContext.getSession().execute(simpleStatement);
            return result.one().getInt(0);
        }catch (DriverException e) {
            log.error("Failed to get count e=", e);
            throw e;
        }
    }

    @Override
    protected URI getURI(CompositeIndexColumnName col) {
        return URI.create(col.getThree());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, CompositeIndexColumnName column) {
        lastMatchedTimeStamp = ((TimeSeriesIndexColumnName)column).getTimeInMicros()/1000;
        return result.createQueryHit(getURI(column));
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return entryType;
    }

    @Override
    public boolean isValid() {
        return indexCF != null && entryType != null;
    }
}
