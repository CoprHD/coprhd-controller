/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.model.Column;

import java.util.*;

/**
 *
 */
public class RemovedColumnsList implements IndexColumnList {
    private Map<String, List<Column<CompositeColumnName>>> _cleanupList;
    private Map<String, Map<String, List<Column<CompositeColumnName>>>> _allColMap;

    public RemovedColumnsList() {
        _cleanupList = new HashMap<>();
        _allColMap = new HashMap<>();
    }

    @Override
    public void add(String key, Column<CompositeColumnName> column) {
        List<Column<CompositeColumnName>> cleanList = _cleanupList.get(key);
        Map<String, List<Column<CompositeColumnName>>> keyColumns = _allColMap.get(key);
        if (cleanList == null) {
            cleanList = new ArrayList<>();
            _cleanupList.put(key, cleanList);
            keyColumns = new HashMap<>();
            _allColMap.put(key, keyColumns);
        }
        cleanList.add(column);

        String colName = column.getName().getOne();
        List<Column<CompositeColumnName>> columns = keyColumns.get(colName);
        if (columns == null) {
            columns = new ArrayList<>();
            keyColumns.put(colName, columns);
        }
        columns.add(column);
    }

    @Override
    public Map<String, List<Column<CompositeColumnName>>> getColumnsToClean() {
        return Collections.unmodifiableMap(_cleanupList);
    }

    @Override
    public Map<String, List<Column<CompositeColumnName>>> getAllColumns(String key) {
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
