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
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;

/**
 * ContainmentPrefixConstrat default implementation
 */
public class ContainmentPrefixConstraintImpl extends ConstraintImpl implements ContainmentPrefixConstraint {
    private static final Logger log = LoggerFactory.getLogger(ContainmentPrefixConstraintImpl.class);

    private URI _indexKey;
    private String _prefix;
    private Keyspace _keyspace;
    private ColumnField _field;

    public ContainmentPrefixConstraintImpl(URI indexKey, String prefix, ColumnField field) {
        super(indexKey, prefix, field);

        _indexKey = indexKey;
        _prefix = prefix;
        _field = field;
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
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query = _keyspace.prepareQuery(_field.getIndexCF())
                .getKey(_indexKey.toString())
                .withColumnRange(_field.buildPrefixRange(_prefix, pageCount));

        return query;
    }

    @Override
    protected URI getURI(Column<IndexColumnName> col) {
        return URI.create(col.getName().getFour());
    }

    @Override
    protected <T> T createQueryHit(final QueryResult<T> result, Column<IndexColumnName> column) {
        return result.createQueryHit(getURI(column), column.getName().getThree(), column.getName().getTimeUUID());
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _field.getDataObjectType();
    }
    
	@Override
	public boolean isValid() {
		return this._indexKey!=null && this._indexKey.toString().length()!=0;
	}
}
