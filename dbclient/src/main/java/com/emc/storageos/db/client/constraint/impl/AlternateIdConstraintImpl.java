/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.query.RowQuery;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer;
import com.netflix.astyanax.serializers.StringSerializer;

/**
 * Alternate ID constraint implementation
 */
public class AlternateIdConstraintImpl extends ConstraintImpl<IndexColumnName> implements AlternateIdConstraint {

    private final ColumnFamily<String, IndexColumnName> _altIdCf;
    private final String _altId;
    private final Class<? extends DataObject> _entryType;
    private Keyspace _keyspace;

    public AlternateIdConstraintImpl(ColumnField field, String altId) {
        super(field, altId);
        indexSerializer = IndexColumnNameSerializer.get();

        _altIdCf = field.getIndexCF();
        _altId = altId;
        _entryType = field.getDataObjectType();
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        queryOnePageWithAutoPaginate(genQuery(), result);
    }

    @Override
    public RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query;
        if (startId == null) {
            query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                    .withColumnRange(CompositeColumnNameSerializer.get().buildRange()
                            .greaterThanEquals(_entryType.getSimpleName())
                            .lessThanEquals(_entryType.getSimpleName())
                            .limit(pageCount));
        }else {
            query = _keyspace.prepareQuery(_altIdCf).getKey(_altId)
                    .withColumnRange(CompositeColumnNameSerializer.get().buildRange()
                            .withPrefix(_entryType.getSimpleName())
                            .greaterThan(startId) // match all column2
                            .lessThan("x") // match all column2
                            .limit(pageCount));

        }
        return query;
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        return result.createQueryHit(getURI(column));
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _entryType;
    }

	@Override
	public boolean isValid() {
        return this._altId!=null && !this._altId.isEmpty();
	}
}
