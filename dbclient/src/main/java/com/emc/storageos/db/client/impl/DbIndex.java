/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.db.client.model.DataObject;

import java.util.Map;
import java.util.List;

public abstract class DbIndex {
    private static final Logger _log = LoggerFactory.getLogger(DbIndex.class);

    protected String fieldName;
    protected ColumnFamily<String, IndexColumnName> indexCF;

    protected boolean indexByKey = false;

    DbIndex(ColumnFamily<String, IndexColumnName> indexCF) {
        this.indexCF = indexCF;
    }

    void setFieldName(String name) {
        this.fieldName = name;
    }

    void setIndexByKey(boolean indexByKey) {
        this.indexByKey = indexByKey;
    }

    ColumnFamily<String, IndexColumnName> getIndexCF() {
        return indexCF;
    }

    public void setIndexCF(ColumnFamily<String, IndexColumnName> cf) {
        indexCF = cf;
    }

    abstract boolean addColumn(String recordKey, CompositeColumnName column, Object value, String className,
            RowMutator mutator, Integer ttl, DataObject obj);

    abstract boolean addColumn(String recordKey, CompositeColumnName column, Object value, String className, RowMutatorDS mutatorDS, Integer ttl, DataObject obj);

    abstract boolean removeColumn(String recordKey, Column<CompositeColumnName> column, String className,
            RowMutator mutator, Map<String, List<Column<CompositeColumnName>>> fieldColumnMap);

    boolean removeColumn(String recordKey, Column<CompositeColumnName> column, String className,
            RowMutator mutator, Map<String, List<Column<CompositeColumnName>>> fieldColumnMap,
            DataObject obj) {
        return removeColumn(recordKey, column, className, mutator, fieldColumnMap);
    }

    public boolean needConsistency() {
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName());

        builder.append("\n");
        builder.append("fieldName:");
        builder.append(fieldName);
        builder.append("\n");

        return builder.toString();
    }
}
