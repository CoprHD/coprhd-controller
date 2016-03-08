/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.ColumnValue;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;

import java.net.URI;

/**
 * Constrained query to get list of decommissioned object URIs of a given type
 */
public class AggregatedConstraintImpl extends ConstraintImpl implements AggregatedConstraint {

    private Keyspace keyspace;
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
    public void setKeyspace(Keyspace keyspace) {
        this.keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        queryOnePageWithAutoPaginate(genQuery(), fieldName, result);
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        return result.createQueryHit(URI.create(column.getName().getTwo()),
                ColumnValue.getPrimitiveColumnValue(column, field.getPropertyDescriptor()));
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        return keyspace.prepareQuery(cf).getKey(rowKey)
                .withColumnRange(
                        CompositeColumnNameSerializer.get().buildRange()
                                .greaterThanEquals(fieldName)
                                .lessThanEquals(fieldName)
                                .limit(pageCount)
                );

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
