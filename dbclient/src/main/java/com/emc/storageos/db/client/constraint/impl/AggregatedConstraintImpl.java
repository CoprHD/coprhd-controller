/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.ColumnValue;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.model.ColumnFamily;

/**
 * Constrained query to get list of decommissioned object URIs of a given type
 */
public class AggregatedConstraintImpl extends ConstraintImpl implements AggregatedConstraint {

    private final ColumnFamily<String, IndexColumnName> cf;
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

        cf = field.getIndexCF();
        entryType = clazz;
        this.field = field;
        fieldName = field.getName();

        rowKey = String.format("%s:%s", clazz.getSimpleName(), groupByValue);
    }

    public AggregatedConstraintImpl(Class<? extends DataObject> clazz, ColumnField field) {

        super(clazz, field);

        cf = field.getIndexCF();
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
    protected URI getURI(IndexColumnName col) {
        return URI.create(col.getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, IndexColumnName column) {
        return result.createQueryHit(URI.create(column.getTwo()),
                ColumnValue.getPrimitiveColumnValue(column.getValue(), field.getPropertyDescriptor()));
    }
    
    @Override
    protected Statement genQueryStatement() {
        StringBuilder queryString = new StringBuilder();
        queryString.append("select").append(" * from \"").append(cf.getName()).append("\"");
        queryString.append(" where key=?");
        queryString.append(" and column1=?");
        
        PreparedStatement preparedStatement = this.dbClientContext.getPreparedStatement(queryString.toString());
        Statement statement =  preparedStatement.bind(rowKey,
                fieldName);
        statement.setFetchSize(pageCount);
        
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
