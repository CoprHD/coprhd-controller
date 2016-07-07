/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class RemovedColumnsList implements IndexColumnList {
    private Map<String, List<CompositeColumnName>> _cleanupList;
    private Map<String, Map<String, List<CompositeColumnName>>> _allColMap;

    public RemovedColumnsList() {
        _cleanupList = new HashMap<>();
        _allColMap = new HashMap<>();
    }

    @Override
    public void add(String key, CompositeColumnName column) {
        List<CompositeColumnName> cleanList = _cleanupList.get(key);
        Map<String, List<CompositeColumnName>> keyColumns = _allColMap.get(key);
        if (cleanList == null) {
            cleanList = new ArrayList<>();
            _cleanupList.put(key, cleanList);
            keyColumns = new HashMap<>();
            _allColMap.put(key, keyColumns);
        }
        cleanList.add(column);

        String colName = column.getOne();
        List<CompositeColumnName> columns = keyColumns.get(colName);
        if (columns == null) {
            columns = new ArrayList<>();
            keyColumns.put(colName, columns);
        }
        columns.add(column);
    }

    @Override
    public Map<String, List<CompositeColumnName>> getColumnsToClean() {
        return Collections.unmodifiableMap(_cleanupList);
    }

    @Override
    public Map<String, List<CompositeColumnName>> getAllColumns(String key) {
        return Collections.unmodifiableMap(_allColMap.get(key));
    }

    @Override
    public boolean isEmpty() {
        return _cleanupList.isEmpty();
    }

    public void clear() {
        _cleanupList.clear();
        _allColMap.clear();
    }

}
