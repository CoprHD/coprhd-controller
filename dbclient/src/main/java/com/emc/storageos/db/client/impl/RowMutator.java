/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.exceptions.OperationTimeoutException;
import com.netflix.astyanax.connectionpool.exceptions.TimeoutException;
import com.netflix.astyanax.connectionpool.exceptions.TokenRangeOfflineException;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.util.TimeUUIDUtils;

/**
 * Encapsulates batch queries for record and index updates
 */
public class RowMutator {
    private static final Logger log = LoggerFactory.getLogger(RowMutator.class);
    
    private Map<String, Map<String, ColumnListMutation<CompositeColumnName>>> _cfRowMap;
    private Map<String, Map<String, ColumnListMutation<IndexColumnName>>> _cfIndexMap;
    private UUID _timeUUID;
    private long _timeStamp;
    private MutationBatch _recordMutator;
    private MutationBatch _indexMutator;
    private Keyspace keyspace;
    private boolean retryFailedWriteWithLocalQuorum = false;
    
    /**
     * Construct RowMutator instance for for index CF and object CF updates
     * 
     * @param keyspace - cassandra keyspace object
     * @param retryWithLocalQuorum - true - retry once with LOCAL_QUORUM for write failure 
     */
    public RowMutator(Keyspace keyspace, boolean retryWithLocalQuorum) {
        this.keyspace = keyspace;
        _timeUUID = TimeUUIDUtils.getUniqueTimeUUIDinMicros();
        _timeStamp = TimeUUIDUtils.getMicrosTimeFromUUID(_timeUUID);

        _recordMutator = keyspace.prepareMutationBatch();
        _indexMutator = keyspace.prepareMutationBatch();
        _recordMutator.setTimestamp(_timeStamp);
        _indexMutator.setTimestamp(_timeStamp);

        _cfRowMap = new HashMap<String, Map<String, ColumnListMutation<CompositeColumnName>>>();
        _cfIndexMap = new HashMap<String, Map<String, ColumnListMutation<IndexColumnName>>>();
        
        this.retryFailedWriteWithLocalQuorum = retryWithLocalQuorum;
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
            executeMutatorWithRetry(_recordMutator);
            executeMutatorWithRetry(_indexMutator);
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Updates index first and record second. This is used for deletion.
     */
    public void executeIndexFirst() {
        try {
            executeMutatorWithRetry(_indexMutator);
            executeMutatorWithRetry(_recordMutator);
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }
    
    /**
     * Retry with LOCAL_QUORUM if remote site is not reachable. See DbClientContext.checkAndResetConsistencyLevel on
     * how the consistency level is changed back after remote site is available again later.
     * 
     * It is supposed to happen on acitve site only.
     * 
     * @param mutator
     * @throws ConnectionException
     */
    private void executeMutatorWithRetry(MutationBatch mutator) throws ConnectionException{
        if (!mutator.isEmpty()) {
            try {
                mutator.execute();
            } catch (TimeoutException | TokenRangeOfflineException | OperationTimeoutException ex) {
                // change consistency level and retry once with LOCAL_QUORUM
                ConsistencyLevel currentConsistencyLevel = keyspace.getConfig().getDefaultWriteConsistencyLevel();
                if (retryFailedWriteWithLocalQuorum && currentConsistencyLevel.equals(ConsistencyLevel.CL_EACH_QUORUM)) {
                    mutator.setConsistencyLevel(ConsistencyLevel.CL_LOCAL_QUORUM);
                    mutator.execute();
                    log.info("Reduce write consistency level to CL_LOCAL_QUORUM");
                    ((AstyanaxConfigurationImpl)keyspace.getConfig()).setDefaultWriteConsistencyLevel(ConsistencyLevel.CL_LOCAL_QUORUM);
                    _indexMutator.setConsistencyLevel(ConsistencyLevel.CL_LOCAL_QUORUM);
                    _recordMutator.setConsistencyLevel(ConsistencyLevel.CL_LOCAL_QUORUM);
                } else {
                    throw ex;
                }
            }
        }
    }
    
}
