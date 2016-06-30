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
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Alternate ID constraint implementation
 */
public class AlternateIdConstraintImpl extends ConstraintImpl implements AlternateIdConstraint {
    private static final Logger log = LoggerFactory.getLogger(AlternateIdConstraintImpl.class);

    private final String _altId;
    private final Class<? extends DataObject> _entryType;

    public AlternateIdConstraintImpl(ColumnField field, String altId) {
        super(field, altId);

        cf = field.getIndexCF().getName();
        _altId = altId;
        _entryType = field.getDataObjectType();
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        StringBuilder queryString = new StringBuilder();
        queryString.append("select * from \"").append(cf).append("\" where key=?");
        List<Object> queryParameters = new ArrayList<Object>();
        queryParameters.add(_altId);
        
        queryOnePageWithoutAutoPaginate(queryString, _entryType.getSimpleName(), result, queryParameters);
    }

    @Override
    protected Statement genQueryStatement() {
        String queryString = String.format("select * from \"%s\" where key=? and column1=?", cf);
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString);
        Statement statement =  preparedStatement.bind(_altId,
                _entryType.getSimpleName());
        statement.setFetchSize(pageCount);
        
        log.info("query string: {}", preparedStatement.getQueryString());
        return statement;
    }

    @Override
    protected URI getURI(IndexColumnName col) {
        return URI.create(col.getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, IndexColumnName column) {
        return result.createQueryHit(getURI(column));
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _entryType;
    }

	@Override
	public boolean isValid() {
        return this._altId!=null && !this._altId.isEmpty();
	}
}
