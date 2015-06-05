/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.constraint.impl;

import java.net.URI;

import com.netflix.astyanax.util.TimeUUIDUtils;
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
 *  default implementation for full name matcher
 */
public class ContainmentLabelConstraintImpl extends ConstraintImpl implements ContainmentPrefixConstraint {
    private static final Logger log = LoggerFactory.getLogger(ContainmentLabelConstraintImpl.class);

    private URI _indexKey;
    private String _prefix;
    private Keyspace _keyspace;
    private ColumnField _field;

    public ContainmentLabelConstraintImpl (URI indexKey, String prefix, ColumnField field) {
        super(indexKey, prefix, field);

        _indexKey = indexKey;
        _prefix = prefix;
        _field = field;
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        _keyspace = keyspace;
    }

    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        queryOnePageWithAutoPaginate(genQuery(), result);
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
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query = _keyspace.prepareQuery(_field.getIndexCF())
                .getKey(_indexKey.toString())
                .withColumnRange(_field.buildMatchRange(_prefix, pageCount));

        return query;
    }

    @Override
    public Class<? extends DataObject> getDataObjectType() {
        return _field.getDataObjectType();
    }
}
