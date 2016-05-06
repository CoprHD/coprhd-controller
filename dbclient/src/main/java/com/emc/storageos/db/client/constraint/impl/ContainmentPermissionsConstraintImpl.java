/*
 * Copyright (c) 2012 EMC Corporation
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
import com.emc.storageos.db.client.constraint.ContainmentPermissionsConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

/**
 * ContainmentPermissions constraint. For example:
 * - find all permissions on a tenant
 * - find all tenants a user has permissions on
 */
public class ContainmentPermissionsConstraintImpl extends ConstraintImpl implements ContainmentPermissionsConstraint {
    private static final Logger log = LoggerFactory.getLogger(ContainmentPermissionsConstraintImpl.class);

    private String _indexKey;
    private String _prefix;
    private Keyspace _keyspace;
    private ColumnField _field;

    public ContainmentPermissionsConstraintImpl(String indexKey, ColumnField field,
            Class<? extends DataObject> clazz) {
        super(indexKey, field, clazz);

        _indexKey = indexKey;
        _prefix = clazz.getSimpleName();
        _field = field;
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        queryOnePageWithoutAutoPaginate(genQuery(), _prefix, result);
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        return URI.create(col.getName().getTwo());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        return result.createQueryHit(getURI(column), column.getName().getThree(), column.getName().getTimeUUID());
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query =
                _keyspace.prepareQuery(_field.getIndexCF()).getKey(_indexKey);

        return query;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _field.getDataObjectType();
    }
    
	@Override
	public boolean isValid() {
        return this._indexKey!=null && !this._indexKey.isEmpty();
	}

}
