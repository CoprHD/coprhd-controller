/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ScopedLabel;

/**
 * find resources matching label
 */
public class LabelConstraintImpl extends ConstraintImpl implements PrefixConstraint {
	private static final Logger log = LoggerFactory.getLogger(LabelConstraintImpl.class);
    private ScopedLabel _label;
    private ColumnField _field;

    public LabelConstraintImpl(String label, ColumnField field) {
        super(label, field);

        _label = new ScopedLabel(null, label.toLowerCase());
        _field = field;
        cf = _field.getIndexCF().getName();
    }

    public LabelConstraintImpl(URI scope, String label, ColumnField field) {
        super(scope, label, field);

        if (scope == null) {
            _label = new ScopedLabel(null, label.toLowerCase());
        } else {
            _label = new ScopedLabel(scope.toString(), label.toLowerCase());
        }
        _field = field;
        cf = _field.getIndexCF().getName();
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        queryOnePageWithAutoPaginate(genQueryStatement(), result);
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
    protected Statement genQueryStatement() {
        String queryString = String.format("select * from \"%s\" where key=? and column1=? and column2=?", cf);
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString.toString());
        Statement statement =  preparedStatement.bind(_field.getPrefixIndexRowKey(_label),
                _field.getDataObjectType().getSimpleName(),
                _label.getLabel());
        statement.setFetchSize(pageCount);
        
        log.info("query string: {}", preparedStatement.getQueryString());
        return statement;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _field.getDataObjectType();
    }
    
	@Override
	public boolean isValid() {
		String key = _field.getPrefixIndexRowKey(_label);
        return key!=null && !key.isEmpty();
	}
}
