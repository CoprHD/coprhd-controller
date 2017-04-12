/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.DataObject;

public abstract class DbIndex <T extends CompositeIndexColumnName> {

    protected String fieldName;
    protected ColumnFamilyDefinition indexCF;
    protected boolean indexByKey = false;

    DbIndex(ColumnFamilyDefinition indexCF) {
        this.indexCF = indexCF;
    }

    void setFieldName(String name) {
        this.fieldName = name;
    }

    void setIndexByKey(boolean indexByKey) {
        this.indexByKey = indexByKey;
    }

    ColumnFamilyDefinition getIndexCF() {
        return indexCF;
    }

    public void setIndexCF(ColumnFamilyDefinition cf) {
        indexCF = cf;
    }

    abstract boolean addColumn(String recordKey, CompositeColumnName column, Object value, String className,
                               RowMutator mutator, Integer ttl, DataObject obj);

    abstract boolean removeColumn(String recordKey, CompositeColumnName column, String className,
            RowMutator mutator, Map<String, List<CompositeColumnName>> fieldColumnMap);

    boolean removeColumn(String recordKey, CompositeColumnName column, String className,
                         RowMutator mutator, Map<String, List<CompositeColumnName>> fieldColumnMap,
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
