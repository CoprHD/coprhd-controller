/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;
import com.emc.storageos.db.client.model.NoInactiveIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;
import com.netflix.astyanax.util.RangeBuilder;
import com.netflix.astyanax.util.TimeUUIDUtils;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Constrained query to get list of decommissioned object URIs of a given type
 */
public class DecommissionedConstraintImpl extends ConstraintImpl implements DecommissionedConstraint {
    private static final Logger log = LoggerFactory.getLogger(DecommissionedConstraintImpl.class);

    private Keyspace _keyspace;
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
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        if (_value != null) {
            queryOnePageWithoutAutoPaginate(genQuery(), Boolean.toString(_value), result);
        }
        else {
            queryOnePageWithAutoPaginate(genQuery(), result);
        }
    }

    @Override
    protected <T> void queryWithAutoPaginate(RowQuery<String, IndexColumnName> query, final QueryResult<T> result,
            final ConstraintImpl constraint) {
        query.autoPaginate(true);
        FilteredQueryHitIterator<T> it;
        if (_timeToStartFrom > 0) {
            // time slice - get only older than _timeToStartFrom
            it = new FilteredQueryHitIterator<T>(query) {
                @Override
                protected T createQueryHit(Column<IndexColumnName> column) {
                    return result.createQueryHit(URI.create(column.getName().getTwo()));
                }

                @Override
                public boolean filter(Column<IndexColumnName> column) {
                    long timeMarked = TimeUUIDUtils.getMicrosTimeFromUUID(column.getName()
                            .getTimeUUID());
                    if (_timeToStartFrom >= timeMarked) {
                        return true;
                    }
                    return false;
                }
            };
        } else {
            // no time slicing - get all
            it = new FilteredQueryHitIterator<T>(query) {
                @Override
                protected T createQueryHit(Column<IndexColumnName> column) {
                    return result.createQueryHit(URI.create(column.getName().getTwo()));
                }

                @Override
                public boolean filter(Column<IndexColumnName> column) {
                    return true;
                }
            };
        }
        it.prime();
        result.setResult(it);
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        return result.createQueryHit(URI.create(column.getName().getTwo()));
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query;
        if (_value == null) {
            query = _keyspace.prepareQuery(_cf).getKey(_rowKey)
                    .withColumnRange(new RangeBuilder().setLimit(pageCount).build());
        } else {
            query = _keyspace.prepareQuery(_cf).getKey(_rowKey)
                    .withColumnRange(
                            CompositeColumnNameSerializer.get().buildRange()
                                    .greaterThanEquals(_value.toString())
                                    .lessThanEquals(_value.toString())
                                    .limit(pageCount));
        }

        return query;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _entryType;
    }
    
	@Override
	public boolean isValid() {
		return this._rowKey!=null && this._rowKey.length()!=0;
	}
}
