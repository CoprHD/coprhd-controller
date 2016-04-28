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

import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.IndexColumnName;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ScopedLabel;

/**
 * Default prefix constraint implementation
 */
public class PrefixConstraintImpl extends ConstraintImpl implements PrefixConstraint {
    private static final Logger log = LoggerFactory.getLogger(PrefixConstraintImpl.class);

    private ScopedLabel _label;
    private ColumnField _field;
    private Keyspace _keyspace;

    public PrefixConstraintImpl(String label, ColumnField field) {
        super(label, field);

        _label = new ScopedLabel(null, label.toLowerCase());
        _field = field;
    }

    public PrefixConstraintImpl(URI scope, String label, ColumnField field) {
        super(scope, label, field);

        if (scope == null) {
            _label = new ScopedLabel(null, label.toLowerCase());
        } else {
            _label = new ScopedLabel(scope.toString(), label.toLowerCase());
        }
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
                .getKey(_field.getPrefixIndexRowKey(_label))
                .withColumnRange(_field.buildPrefixRange(_label.getLabel(), pageCount));

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
		String key = _field.getPrefixIndexRowKey(_label);
        return key!=null && !key.isEmpty();
	}
}
