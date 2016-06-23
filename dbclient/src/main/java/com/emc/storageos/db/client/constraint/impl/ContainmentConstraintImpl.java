/*
 * Copyright (c) 2008-2011 EMC Corporation
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
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.impl.AltIdDbIndex;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.RelationDbIndex;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Abstract base for all containment queries
 */
public class ContainmentConstraintImpl extends ConstraintImpl implements ContainmentConstraint {
    private static final Logger log = LoggerFactory.getLogger(ContainmentConstraintImpl.class);

    private URI _indexKey;
    private Class<? extends DataObject> _entryType;
    private final ColumnField _field;

    public ContainmentConstraintImpl(URI indexKey, Class<? extends DataObject> entryType, ColumnField field) {
        super(indexKey, entryType, field);

        _indexKey = indexKey;
        _entryType = entryType;
        _field = field;
    }
    
    @Override
    protected Statement genQueryStatement() {
        StringBuilder queryString = new StringBuilder();
        queryString.append("select").append(" * from \"").append(_field.getIndexCF().getName()).append("\"");
        queryString.append(" where key=?");
        queryString.append(" and column1=?");
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString.toString());
        Statement statement =  preparedStatement.bind(_indexKey.toString(),
                _entryType.getSimpleName());
        statement.setFetchSize(pageCount);
        
        log.info("query string: {}", preparedStatement.getQueryString());
        return statement;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        StringBuilder queryString = new StringBuilder();
        queryString.append("select").append(" * from \"").append(_field.getIndexCF().getName()).append("\"");
        queryString.append(" where key=?");
        
        if (startId != null && _field.getIndex() instanceof RelationDbIndex) {
            List<Object> queryParameters = new ArrayList<Object>();
            queryParameters.add(_indexKey.toString());
            
            queryOnePageWithoutAutoPaginate(queryString, _entryType.getSimpleName(), result, queryParameters);
            return;
        }

        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString.toString());
        Statement statement =  preparedStatement.bind(_indexKey.toString());
        statement.setFetchSize(pageCount);
        
        log.info("query string: {}", preparedStatement.getQueryString());
        queryOnePageWithAutoPaginate(statement, result);
    }

    @Override
    protected URI getURI(IndexColumnName col) {
        URI ret;
        if (_field.getIndex() instanceof RelationDbIndex) {
            ret = URI.create(col.getTwo());
        } else if (_field.getIndex() instanceof AltIdDbIndex) {
            ret = URI.create(col.getTwo());
        } else {
            ret = URI.create(col.getFour());
        }

        return ret;
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, IndexColumnName column) {
        if (_field.getIndex() instanceof RelationDbIndex) {
            return result.createQueryHit(getURI(column));
        } else {
            return result.createQueryHit(getURI(column), column.getThree(), column.getTimeUUID());
        }
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _field.getDataObjectType();
    }

	@Override
	public boolean isValid() {
        return this._indexKey!=null && !this._indexKey.toString().isEmpty();
	}
}
