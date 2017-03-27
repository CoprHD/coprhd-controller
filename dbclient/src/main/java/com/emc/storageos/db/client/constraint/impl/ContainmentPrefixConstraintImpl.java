/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeIndexColumnName;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

/**
 * ContainmentPrefixConstrat default implementation
 */
public class ContainmentPrefixConstraintImpl extends ConstraintImpl implements ContainmentPrefixConstraint {
	private static final Logger log = LoggerFactory.getLogger(ContainmentPrefixConstraintImpl.class);
    private URI _indexKey;
    private String _prefix;
    private ColumnField _field;

    public ContainmentPrefixConstraintImpl(URI indexKey, String prefix, ColumnField field) {
        super(indexKey, prefix, field);

        _indexKey = indexKey;
        _prefix = prefix;
        _field = field;
        cf = _field.getIndexCF().getName();
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        queryOnePageWithAutoPaginate(genQueryStatement(), result);
    }
    
    @Override
    protected Statement genQueryStatement() {
        String queryString = String.format("select * from \"%s\" where key=? and column1=? and column2>=? and column2<=?", cf);
        
        String target = _prefix.toLowerCase();
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString);
        Statement statement =  preparedStatement.bind(_indexKey.toString(),
                _field.getDataObjectType().getSimpleName(),
                target, target + Character.MAX_VALUE);
        statement.setFetchSize(pageCount);
        
        log.debug("query string: {}", preparedStatement.getQueryString());
        return statement;
    }

    @Override
    protected URI getURI(CompositeIndexColumnName col) {
        return URI.create(col.getFour());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, CompositeIndexColumnName column) {
        return result.createQueryHit(getURI(column), column.getThree(), column.getTimeUUID());
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
