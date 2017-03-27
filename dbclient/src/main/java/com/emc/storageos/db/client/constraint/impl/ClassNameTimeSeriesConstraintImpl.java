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
import com.emc.storageos.db.client.impl.ClassNameTimeSeriesIndexColumnName;
import com.emc.storageos.db.client.impl.ColumnFamilyDefinition;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeIndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

public class ClassNameTimeSeriesConstraintImpl extends ConstraintImpl
        implements TimeSeriesConstraint {
    private static final Logger log = LoggerFactory.getLogger(ClassNameTimeSeriesConstraintImpl.class);

    private final ColumnFamilyDefinition _altIdCf;
    private final String _altId;
    private final Class<? extends DataObject> _entryType;
    private long startTimeMicros;
    private long endTimeMicros;
    private long lastMatchedTimeStamp = 0;

    public ClassNameTimeSeriesConstraintImpl(ColumnField field, String altId, long startTimeInMS, long endTimeInMS) {
        super(field, altId);

        _altIdCf = field.getIndexCF();
        _altId = altId;
        _entryType = field.getDataObjectType();
        startTimeMicros = startTimeInMS*1000L;
        endTimeMicros = endTimeInMS * 1000L;
    }
    
    @Override
    protected Statement genQueryStatement() {
    	String queryString = String.format("select * from \"%s\" where key='%s' and column1='%s' and column2>%s and column2<%s", 
    			_altIdCf.getName(), _altId, _entryType.getSimpleName(), startTimeMicros, endTimeMicros);
        
        SimpleStatement simpleStatement = new SimpleStatement(queryString);
        simpleStatement.setFetchSize(pageCount);
        
        log.debug("query string: {}", queryString);
        return simpleStatement;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        queryOnePageWithAutoPaginate(genQueryStatement(),result);
    }

    public long getLastMatchedTimeStamp() {
        return lastMatchedTimeStamp;
    }

    @Override
    public long count() throws DriverException {
    	try {
    		String queryString = String.format("select * from \"%s\" where key='%s' and column1='%s' and column2>%s and column2<%s", 
        			_altIdCf.getName(), _altId, _entryType.getSimpleName(), startTimeMicros, endTimeMicros);
            
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
        lastMatchedTimeStamp = ((ClassNameTimeSeriesIndexColumnName)column).getTimeInMicros()/1000;
        return result.createQueryHit(getURI(column));
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _entryType;
    }

    @Override
    public boolean isValid() {
        return _altId != null && !_altId.isEmpty();
    }
}

