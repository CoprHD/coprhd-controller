/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

/**
 * ContainmentPrefixConstrat default implementation
 */
public class ContainmentPrefixConstraintImpl extends ConstraintImpl implements ContainmentPrefixConstraint {

    private URI _indexKey;
    private String _prefix;
    private ColumnField _field;

    public ContainmentPrefixConstraintImpl(URI indexKey, String prefix, ColumnField field) {
        super(indexKey, prefix, field);

        _indexKey = indexKey;
        _prefix = prefix;
        _field = field;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        queryOnePageWithAutoPaginate(genQueryStatement(), result);
    }
    
    @Override
    protected Statement genQueryStatement() {
        StringBuilder queryString = new StringBuilder();
        queryString.append("select").append(" * from \"").append(_field.getIndexCF().getName()).append("\"");
        queryString.append(" where key=?");
        String target = _prefix.toLowerCase();
        queryString.append(" and column1=?");
        queryString.append(" and column2>=? and column2<=?");
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString.toString());
        Statement statement =  preparedStatement.bind(_indexKey.toString(),
                _field.getDataObjectType().getSimpleName(),
                target, target + Character.MAX_VALUE);
        statement.setFetchSize(pageCount);
        
        return statement;
    }

    @Override
    protected URI getURI(IndexColumnName col) {
        return URI.create(col.getFour());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, IndexColumnName column) {
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
