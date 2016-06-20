/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.impl.AltIdDbIndex;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.impl.RelationDbIndex;
import com.emc.storageos.db.client.model.DataObject;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.query.RowQuery;

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
    protected URI getURI(IndexColumnName col) {
        URI ret;
        if (_field.getIndex() instanceof RelationDbIndex) {
            ret = URI.create(col.getTwo());
        } else if (_field.getIndex() instanceof AltIdDbIndex) {
            ret = URI.create(col.getTwo());
        } else {
            ret = URI.create(col.getFour());
        }

        return ret;
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, IndexColumnName column) {
        if (_field.getIndex() instanceof RelationDbIndex) {
            return result.createQueryHit(getURI(column));
        } else {
            return result.createQueryHit(getURI(column), column.getThree(), column.getTimeUUID());
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
