/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.query.RowQuery;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.client.model.DataObject;

/**
 * Abstract base for all containment queries
 */
public class ContainmentConstraintImpl extends ConstraintImpl implements ContainmentConstraint {
    private static final Logger log = LoggerFactory.getLogger(ContainmentConstraintImpl.class);

    private URI _indexKey;
    private Class<? extends DataObject> _entryType;
    private final ColumnField _field;
    private Keyspace _keyspace;

    public ContainmentConstraintImpl(URI indexKey, Class<? extends DataObject> entryType, ColumnField field) {
        super(indexKey, entryType, field);

        _indexKey = indexKey;
        _entryType = entryType;
        _field = field;
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query = _keyspace
                .prepareQuery(_field.getIndexCF())
                .getKey(_indexKey.toString())
                .withColumnRange(
                        CompositeColumnNameSerializer.get().buildRange()
                                .greaterThanEquals(_entryType.getSimpleName())
                                .lessThanEquals(_entryType.getSimpleName())
                                .limit(pageCount));
        return query;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        RowQuery<String, IndexColumnName> query = _keyspace.prepareQuery(_field.getIndexCF()).getKey(_indexKey.toString());

        if (startId != null && _field.getIndex() instanceof RelationDbIndex) {
            queryOnePageWithoutAutoPaginate(query, _entryType.getSimpleName(), result);
            return;
        }

        queryOnePageWithAutoPaginate(query, _entryType.getSimpleName(), result);
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        URI ret;
        if (_field.getIndex() instanceof RelationDbIndex) {
            ret = URI.create(col.getName().getTwo());
        } else if (_field.getIndex() instanceof AltIdDbIndex) {
            ret = URI.create(col.getName().getTwo());
        } else {
            ret = URI.create(col.getName().getFour());
        }

        return ret;
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        if (_field.getIndex() instanceof RelationDbIndex) {
            return result.createQueryHit(getURI(column));
        } else {
            return result.createQueryHit(getURI(column), column.getName().getThree(), column.getName().getTimeUUID());
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
