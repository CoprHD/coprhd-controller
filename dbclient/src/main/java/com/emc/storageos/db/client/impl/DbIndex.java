/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.Map;
import java.util.List;

import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.Column;

import com.emc.storageos.db.client.model.DataObject;

public abstract class DbIndex <T extends CompositeIndexColumnName> {

    protected String fieldName;
    protected ColumnFamily<String, T> indexCF;

    protected boolean indexByKey = false;

    DbIndex(ColumnFamily<String, T> indexCF) {
        this.indexCF = indexCF;
    }

    void setFieldName(String name) {
        this.fieldName = name;
    }

    void setIndexByKey(boolean indexByKey) {
        this.indexByKey = indexByKey;
    }

    ColumnFamily<String, T> getIndexCF() {
        return indexCF;
    }

    public void setIndexCF(ColumnFamily<String, T> cf) {
        indexCF = cf;
    }

    abstract boolean addColumn(String recordKey, CompositeColumnName column, Object value, String className,
                               RowMutator mutator, Integer ttl, DataObject obj);

    abstract boolean removeColumn(String recordKey, Column<CompositeColumnName> column, String className,
                                  RowMutator mutator, Map<String, List<Column<CompositeColumnName>>> fieldColumnMap);

    boolean removeColumn(String recordKey, Column<CompositeColumnName> column, String className,
                         RowMutator mutator, Map<String, List<Column<CompositeColumnName>>> fieldColumnMap, DataObject obj) {
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
