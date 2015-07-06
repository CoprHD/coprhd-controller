/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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


/**
 * Alternate ID constraint implementation
 */
public class AlternateIdConstraintImpl extends ConstraintImpl implements AlternateIdConstraint {
    private final ColumnFamily<String, IndexColumnName> altIdCf;
    private final String altId;
    private final Class<? extends DataObject> entryType;
    private Keyspace keyspace;

    public AlternateIdConstraintImpl(ColumnField field, String altId) {
        super(field, altId);

        this.altIdCf = field.getIndexCF();
        this.altId = altId;
        this.entryType = field.getDataObjectType();
    }

    @Override
    public void setKeyspace(Keyspace keyspace) {
        this.keyspace = keyspace;
    }

    @Override
    protected <T> void queryOnePage(final QueryResult<T> result) throws ConnectionException {
        queryOnePageWithoutAutoPaginate(genQuery(), entryType.getSimpleName(), result);
    }

    @Override
    protected RowQuery<String, IndexColumnName> genQuery() {
        RowQuery<String, IndexColumnName> query = keyspace.prepareQuery(altIdCf).getKey(altId)
                .withColumnRange(CompositeColumnNameSerializer.get().buildRange()
                        .greaterThanEquals(entryType.getSimpleName())
                        .lessThanEquals(entryType.getSimpleName())
                        .limit(pageCount));
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
        return entryType;
    }
}
