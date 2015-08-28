/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.util.TimeUUIDUtils;

/**
 * Encapsulates batch queries for record and index updates
 */
public class RowMutator {
    private Map<String, Map<String, ColumnListMutation<CompositeColumnName>>> _cfRowMap;
    private Map<String, Map<String, ColumnListMutation<IndexColumnName>>> _cfIndexMap;
    private UUID _timeUUID;
    private long _timeStamp;
    private MutationBatch _recordMutator;
    private MutationBatch _indexMutator;

    public RowMutator(Keyspace keyspace) {
        _timeUUID = TimeUUIDUtils.getUniqueTimeUUIDinMicros();
        _timeStamp = TimeUUIDUtils.getMicrosTimeFromUUID(_timeUUID);

        _recordMutator = keyspace.prepareMutationBatch();
        _indexMutator = keyspace.prepareMutationBatch();
        _recordMutator.setTimestamp(_timeStamp);
        _indexMutator.setTimestamp(_timeStamp);

        _cfRowMap = new HashMap<String, Map<String, ColumnListMutation<CompositeColumnName>>>();
        _cfIndexMap = new HashMap<String, Map<String, ColumnListMutation<IndexColumnName>>>();
    }

    public UUID getTimeUUID() {
        return _timeUUID;
    }

    public long getTimeStamp() {
        return _timeStamp;
    }

    /**
     * Get record row for given CF
     * 
     * @param cf
     * @param key
     * @return
     */
    public ColumnListMutation<CompositeColumnName> getRecordColumnList(
            ColumnFamily<String, CompositeColumnName> cf, String key) {
        Map<String, ColumnListMutation<CompositeColumnName>> rowMap = _cfRowMap.get(cf.getName());
        if (rowMap == null) {
            rowMap = new HashMap<String, ColumnListMutation<CompositeColumnName>>();
            _cfRowMap.put(cf.getName(), rowMap);
        }
        ColumnListMutation<CompositeColumnName> row = rowMap.get(key);
        if (row == null) {
            row = _recordMutator.withRow(cf, key);
            rowMap.put(key, row);
        }
        return row;
    }

    /***
     * Get index row for given CF
     * 
     * @param cf
     * @param key
     * @return
     */
    public ColumnListMutation<IndexColumnName> getIndexColumnList(
            ColumnFamily<String, IndexColumnName> cf, String key) {
        Map<String, ColumnListMutation<IndexColumnName>> rowMap = _cfIndexMap.get(cf.getName());
        if (rowMap == null) {
            rowMap = new HashMap<String, ColumnListMutation<IndexColumnName>>();
            _cfIndexMap.put(cf.getName(), rowMap);
        }
        ColumnListMutation<IndexColumnName> row = rowMap.get(key);
        if (row == null) {
            row = _indexMutator.withRow(cf, key);
            rowMap.put(key, row);
        }
        return row;
    }

    /**
     * Updates record first and index second. This is used for insertion
     */
    public void executeRecordFirst() {
        try {
            if (!_recordMutator.isEmpty()) {
                _recordMutator.execute();
            }
            if (!_indexMutator.isEmpty()) {
                _indexMutator.execute();
            }
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Updates index first and record second. This is used for deletion.
     */
    public void executeIndexFirst() {
        try {
            if (!_indexMutator.isEmpty()) {
                _indexMutator.execute();
            }
            if (!_recordMutator.isEmpty()) {
                _recordMutator.execute();
            }
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }
}
