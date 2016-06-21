/*
 * Copyright (c) 2012 EMC Corporation
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
import com.emc.storageos.db.client.constraint.ContainmentPermissionsConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

/**
 * ContainmentPermissions constraint. For example:
 * - find all permissions on a tenant
 * - find all tenants a user has permissions on
 */
public class ContainmentPermissionsConstraintImpl extends ConstraintImpl implements ContainmentPermissionsConstraint {
    private static final Logger log = LoggerFactory.getLogger(ContainmentPermissionsConstraintImpl.class);

    private String _indexKey;
    private String _prefix;
    private ColumnField _field;

    public ContainmentPermissionsConstraintImpl(String indexKey, ColumnField field,
            Class<? extends DataObject> clazz) {
        super(indexKey, field, clazz);

        _indexKey = indexKey;
        _prefix = clazz.getSimpleName();
        _field = field;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        StringBuilder queryString = generateQueryString();
        
        List<Object> queryParameters = new ArrayList<Object>();
        queryParameters.add(_indexKey.toString());
        
        queryOnePageWithoutAutoPaginate(queryString, _prefix, result, queryParameters);
    }

    @Override
    protected URI getURI(IndexColumnName col) {
        return URI.create(col.getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, IndexColumnName column) {
        return result.createQueryHit(getURI(column), column.getThree(), column.getTimeUUID());
    }
    
    @Override
    protected Statement genQueryStatement() {
        StringBuilder queryString = generateQueryString();
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString.toString());
        Statement statement =  preparedStatement.bind(_indexKey.toString());
        statement.setFetchSize(pageCount);
        
        return statement;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _field.getDataObjectType();
    }
    
	@Override
	public boolean isValid() {
        return this._indexKey!=null && !this._indexKey.isEmpty();
	}
	
	private StringBuilder generateQueryString() {
        StringBuilder queryString = new StringBuilder();
        queryString.append("select").append(" * from \"").append(_field.getIndexCF().getName()).append("\"");
        queryString.append(" where key=?");
        return queryString;
    }
}
