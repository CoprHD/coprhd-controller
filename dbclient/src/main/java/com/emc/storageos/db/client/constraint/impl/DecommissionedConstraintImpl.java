/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NoInactiveIndex;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.util.TimeUUIDUtils;

/**
 * Constrained query to get list of decommissioned object URIs of a given type
 */
public class DecommissionedConstraintImpl extends ConstraintImpl implements DecommissionedConstraint {
    private static final Logger log = LoggerFactory.getLogger(DecommissionedConstraintImpl.class);

    private final ColumnFamily<String, IndexColumnName> _cf;
    private final String _rowKey;
    private final long _timeToStartFrom;
    private final Boolean _value;
    private final Class<? extends DataObject> _entryType;

    /*
     * Constraint for listing all active objects of a given type with value true
     * used by GC and token time indexing
     */
    public DecommissionedConstraintImpl(Class<? extends DataObject> clazz, ColumnField field, long timeStart) {
        super(clazz, field, timeStart);

        throwIfNoInactiveIndex(field);

        _cf = field.getIndexCF();
        _rowKey = clazz.getSimpleName();
        _timeToStartFrom = timeStart;
        _value = true;
        _entryType = clazz;
    }

    /*
     * Constraint for listing all objects of a given type with index value
     * if value is null, gives full list for the type - used by queryByType.
     */
    public DecommissionedConstraintImpl(Class<? extends DataObject> clazz, ColumnField field, Boolean value) {
        super(clazz, field, value);

        throwIfNoInactiveIndex(field);

        _cf = field.getIndexCF();
        _rowKey = clazz.getSimpleName();
        _timeToStartFrom = 0;
        _value = value;
        _entryType = clazz;
    }

    private void throwIfNoInactiveIndex(ColumnField field) {
        if (field.getName().equals(DataObject.INACTIVE_FIELD_NAME)
                && field.getDataObjectType().getAnnotation(NoInactiveIndex.class) != null) {
            throw new IllegalArgumentException(String.format("Class %s is marked with @NoInactiveIndex", field.getDataObjectType()
                    .getName()));
        }
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        if (_value != null) {
            StringBuilder queryString = new StringBuilder();
            queryString.append("select").append(" * from \"").append(_cf.getName()).append("\"");
            queryString.append(" where key=?");
            
            List<Object> queryParameters = new ArrayList<Object>();
            queryParameters.add(_rowKey);
            queryOnePageWithoutAutoPaginate(queryString, Boolean.toString(_value), result, queryParameters);
        }
        else {
            queryOnePageWithAutoPaginate(genQueryStatement(), result);
        }
    }

    
    protected <T> void queryWithAutoPaginate(Statement statement, final QueryResult<T> result,
            final ConstraintImpl constraint) {
        FilteredQueryHitIterator<T> it;
        if (_timeToStartFrom > 0) {
            // time slice - get only older than _timeToStartFrom
            it = new FilteredQueryHitIterator<T>(dbClientContext, statement) {
                @Override
                protected T createQueryHit(IndexColumnName column) {
                    return result.createQueryHit(URI.create(column.getTwo()));
                }

                @Override
                public boolean filter(IndexColumnName column) {
                    long timeMarked = TimeUUIDUtils.getMicrosTimeFromUUID(column.getTimeUUID());
                    if (_timeToStartFrom >= timeMarked) {
                        return true;
                    }
                    return false;
                }
            };
        } else {
            // no time slicing - get all
            it = new FilteredQueryHitIterator<T>(dbClientContext, statement) {
                @Override
                protected T createQueryHit(IndexColumnName column) {
                    return result.createQueryHit(URI.create(column.getTwo()));
                }

                @Override
                public boolean filter(IndexColumnName column) {
                    return true;
                }
            };
        }
        it.prime();
        result.setResult(it);
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
        List<Object> queryParameters = new ArrayList<Object>();
        
        StringBuilder queryString = new StringBuilder();
        queryString.append("select").append(" * from \"").append(_cf.getName()).append("\"");
        queryString.append(" where key=?");
        queryParameters.add(_rowKey);
        
        if (_value != null) {
            queryString.append(" and column1=?");
            queryParameters.add(_value.toString());
        }
        
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString.toString());
        Statement statement =  preparedStatement.bind(queryParameters.toArray(new Object[]{0}));
        statement.setFetchSize(pageCount);
        
        log.info("query string: {}", preparedStatement.getQueryString());
        return statement;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _entryType;
    }
    
	@Override
	public boolean isValid() {
        return this._rowKey!=null && !this._rowKey.isEmpty();
	}
}
