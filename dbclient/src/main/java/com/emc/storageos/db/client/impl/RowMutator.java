/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * Encapsulates batch queries for record and index updates
 */
public class RowMutator<T extends CompositeIndexColumnName> {
    private static final Logger log = LoggerFactory.getLogger(RowMutator.class);

    //this offset is to make sure timeuuid is unique for each data colun to resolve COP-26680
    private static int TIME_STAMP_OFFSET = 1;

    private Map<String, Map<String, ColumnListMutation<CompositeColumnName>>> _cfRowMap;
    private Map<String, Map<String, ColumnListMutation<T>>> _cfIndexMap;
    private AtomicLong _timeStamp = new AtomicLong();
    private MutationBatch _mutationBatch;
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

        long microsTimeStamp = TimeUUIDUtils.getMicrosTimeFromUUID(TimeUUIDUtils.getUniqueTimeUUIDinMicros());
        this._timeStamp.set(microsTimeStamp);
        _mutationBatch = keyspace.prepareMutationBatch();
        _mutationBatch.setTimestamp(microsTimeStamp).withAtomicBatch(true);

        _cfRowMap = new HashMap<String, Map<String, ColumnListMutation<CompositeColumnName>>>();
        _cfIndexMap = new HashMap<String, Map<String, ColumnListMutation<T>>>();

        this.retryFailedWriteWithLocalQuorum = retryWithLocalQuorum;
    }

    public UUID getTimeUUID() {
        return TimeUUIDUtils.getMicrosTimeUUID(_timeStamp.addAndGet(TIME_STAMP_OFFSET));
    }

    public void resetTimeUUIDStartTime(long startTime) {
        _timeStamp.set(startTime);
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
            row = _mutationBatch.withRow(cf, key);
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
    public ColumnListMutation<T> getIndexColumnList(
            ColumnFamily<String, T> cf, String key) {
        Map<String, ColumnListMutation<T>> rowMap = _cfIndexMap.get(cf.getName());

        if (rowMap == null) {
            rowMap = new HashMap<String, ColumnListMutation<T>>();
            _cfIndexMap.put(cf.getName(), rowMap);
        }

        ColumnListMutation<T> row = rowMap.get(key);
        if (row == null) {
            row = _mutationBatch.withRow(cf, key);
            rowMap.put(key, row);
        }
        return row;
    }

    /**
     * Updates record and index with atomic batch
     */
    public void execute() {
        try {
            executeMutatorWithRetry(_mutationBatch);
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Retry with LOCAL_QUORUM if remote site is not reachable. See DbClientContext.checkAndResetConsistencyLevel on
     * how the consistency level is changed back after remote site is available again later.
     *
     * It is supposed to happen on active site only.
     *
     * @param mutator
     * @throws ConnectionException
     */
    private void executeMutatorWithRetry(MutationBatch mutator) throws ConnectionException {
        if (mutator.isEmpty()) {
            return;
        }

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
                _mutationBatch.setConsistencyLevel(ConsistencyLevel.CL_LOCAL_QUORUM);
            } else {
                throw ex;
            }
        }
    }
}
