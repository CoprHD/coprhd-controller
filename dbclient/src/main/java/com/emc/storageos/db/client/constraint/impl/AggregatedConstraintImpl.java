/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.ColumnValue;
import com.emc.storageos.db.client.impl.CompositeIndexColumnName;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Constrained query to get list of decommissioned object URIs of a given type
 */
public class AggregatedConstraintImpl extends ConstraintImpl implements AggregatedConstraint {
	private static final Logger log = LoggerFactory.getLogger(AggregatedConstraintImpl.class);
    private final ColumnField field;
    private final String fieldName;
    private final String rowKey;
    private final Class<? extends DataObject> entryType;

    /*
     * Constraint for listing all objects of a given type with index value
     * if value is null, gives full list for the type - used by queryByType.
     */
    public AggregatedConstraintImpl(Class<? extends DataObject> clazz, ColumnField groupByField, String groupByValue, ColumnField field) {

        super(clazz, field, groupByField.getName(), groupByValue);

        cf = field.getIndexCF().getName();
        entryType = clazz;
        this.field = field;
        fieldName = field.getName();

        rowKey = String.format("%s:%s", clazz.getSimpleName(), groupByValue);
    }

    public AggregatedConstraintImpl(Class<? extends DataObject> clazz, ColumnField field) {

        super(clazz, field);

        cf = field.getIndexCF().getName();
        entryType = clazz;
        this.field = field;
        fieldName = field.getName();

        rowKey = clazz.getSimpleName();
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws DriverException {
        queryOnePageWithAutoPaginate(genQueryStatement(), result);
    }

    @Override
    protected URI getURI(CompositeIndexColumnName col) {
        return URI.create(col.getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, CompositeIndexColumnName column) {
        return result.createQueryHit(URI.create(column.getTwo()),
                ColumnValue.getPrimitiveColumnValue(((IndexColumnName)column).getValue(), field.getPropertyDescriptor()));
    }
    
    @Override
    protected Statement genQueryStatement() {
        String queryString = String.format("select * from \"%s\" where key=? and column1=?", cf);
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString);
        Statement statement = preparedStatement.bind(rowKey,
                fieldName);
        statement.setFetchSize(pageCount);
        
        log.debug("query string: {}", preparedStatement.getQueryString());
        return statement;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return entryType;
    }

	@Override
	public boolean isValid() {
        return this.rowKey!=null && !this.rowKey.isEmpty();
	}
}
