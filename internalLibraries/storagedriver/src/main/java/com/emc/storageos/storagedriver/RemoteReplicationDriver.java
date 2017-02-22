/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver;


import java.util.List;

import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationOperationContext;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * This class defines driver interface methods for remote replication.
 *
 * RemoteReplicationContext parameter, defined in the remote replication link operations, specifies remote replication set and group
 * containers for which link operation was initiated by the controller. For example, if system operation was executed for remote replication group,
 * the context will specify group element type, native if of the group replication set and native id of the group.
 * Other example, if controller operation was executed for individual pairs directly, the context will specify pair element type. In this case native ids
 * of remote replication set and group are not specified.
 *
 * Driver may use RemoteReplicationContext parameter to check validity of remote replication link operation. For example, if request has
 * remote replication group context and only subset of remote replication pairs from the system group are specified (which indicates that controller has
 * stale information about system configuration), driver may fail this request.
 */
public interface RemoteReplicationDriver {

    /**
     * Create empty remote replication group.
     * @param replicationGroup specifies properties of remote replication group to create.
     * @param capabilities storage capabilities for the group
     * @return driver task
     */
    public DriverTask createRemoteReplicationGroup(RemoteReplicationGroup replicationGroup, StorageCapabilities capabilities);

    /**
     * Create replication pairs in existing replication group container.
     * At the completion of this operation each created remote replication pair on device should be associated to its group and should
     * be in the following state, as defined for the pair in the replicationPairs argument:
     *  a) Pair state: ACTIVE;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     data on R2 element is synchronized with R1 data (R1 data is copied to R2);
     *     replication link between R1 and R2 elements on device is set to ready state;
     *
     *  b) Pair state: SPLIT;
     *     R1 element should be read/write enabled;
     *     R2 element should be read/write enabled;
     *     replication link between R1 and R2 elements on device is set to not-ready state;
     *     The state of replication pairs is same as after 'split' operation.
     *
     * @param replicationPairs list of replication pairs to create; each pair is in either active or split state
     * @param capabilities storage capabilities for the pairs
     * @return driver task
     */
    public DriverTask createGroupReplicationPairs(List<RemoteReplicationPair> replicationPairs, StorageCapabilities capabilities);

    /**
     * Create replication pairs in existing replication set. Pairs are created outside of group container.
     *
     * At the completion of this operation each created remote replication pair on device should be associated to its set and should
     * be in the following state, as defined for the pair in the replicationPairs argument:
     *  a) Pair state: ACTIVE;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     data on R2 element is synchronized with R1 data (R1 data is copied to R2);
     *     replication link between R1 and R2 elements on device is set to ready state;
     *
     *  b) Pair state: SPLIT;
     *     R1 element should be read/write enabled;
     *     R2 element should be read/write enabled;
     *     replication link between R1 and R2 elements on device is set to not-ready state;
     *     The state of replication pairs is same as after 'split' operation.
     *
     * @param replicationPairs list of replication pairs to create; each pair is in either active or split state
     * @param capabilities storage capabilities for the pairs
     * @return driver task
     */
    public DriverTask createSetReplicationPairs(List<RemoteReplicationPair> replicationPairs, StorageCapabilities capabilities);

    /**
     * Delete remote replication pairs. Should not delete backend volumes.
     * Only should affect remote replication configuration on array.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * At the completion of this operation all remote replication elements from the replicationPairs
     * should be in the following state:
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links between R1 and R2 elements on device are disabled;
     *     R1 and R2 elements are disassociated from their remote replication containers and
     *     become independent storage elements;
     *
     * @param replicationPairs replication pairs to delete
     * @param capabilities storage capabilities for the pairs
     * @return  driver task
     */
    public DriverTask deleteReplicationPairs(List<RemoteReplicationPair> replicationPairs, StorageCapabilities capabilities);


    // replication link operations

    /**
     * Suspend remote replication link for remote replication pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * Before this operation replication link for each pair in the request should be in ready state.
     *
     * At the completion of this operation all remote replication pairs should be in the following state:
     *     Pair state: SUSPENDED;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     replication link on device should be in not ready state;
     *
     * @param replicationPairs: remote replication pairs to suspend
     * @param context: context information for this operation
     * @param capabilities storage capabilities for this operation
     * @return driver task
     */
    public DriverTask suspend(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                              StorageCapabilities capabilities);

    /**
     * Resume remote replication link for remote replication pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * Before this operation replication link for each pair in the request should be in not ready state.
     *
     * At the completion of this operation all remote replication pairs specified in the request should
     * be in the following state:
     *     Pair state: ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data (R1 data copied to R2);
     *     replication link on device should be in ready state;
     *
     * @param replicationPairs: remote replication pairs to resume
     * @param capabilities storage capabilities for this operation
     * @param context: context information for this operation
     * @return driver task
     */
    public DriverTask resume(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                             StorageCapabilities capabilities);

    /**
     * Split remote replication link for remote replication pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * Before this operation replication link for each pair in the request should be in ready state.
     *
     * At the completion of this operation all remote replication pairs specified in the request should
     * be in the following state:
     *     Pair state: SPLIT;
     *     No change in remote replication container (group/set) for the pair;
     *     R1 element should be read/write enabled;
     *     R2 element should be read/write enabled;
     *     replication link on device should be in not ready state;
     *
     * @param replicationPairs: remote replication pairs to split
     * @param capabilities storage capabilities for this operation
     * @param context: context information for this operation
     * @return driver task
     */
    public DriverTask split(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                            StorageCapabilities capabilities);

    /**
     * Establish replication link for remote replication pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * This operation creates replication links for replication pairs on device.
     *
     * At the completion of this operation all remote replication pairs specified in the request should
     * be in the following state:
     *     Pair state: ACTIVE;
     *     No change in remote replication container (group/set) for the pair;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     data on R2 element is synchronized with R1 data(R1 data copied to R2);
     *     replication link on device should be in ready state;
     *
     * @param replicationPairs remote replication pairs to establish
     * @param capabilities storage capabilities for this operation
     * @param context: context information for this operation
     * @return driver task
     */
    public DriverTask establish(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                                StorageCapabilities capabilities);

    /**
     * Failover remote replication link for remote replication pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * At the completion of this operation all remote replication pairs specified in the request should
     * be in the following state:
     *     Pair state: FAILED_OVER;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 element should be write disabled;
     *     R2 element should be read/write enabled;
     *     replication links should be in not ready state;
     *
     * @param replicationPairs: remote replication pairs to failover
     * @param context: context information for this operation
     * @param capabilities storage capabilities for this operation
     * @return driver task
     */
    public DriverTask failover(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                               StorageCapabilities capabilities);

    /**
     * Failback (restore) remote replication link for remote replication pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * Before this operation replication link for each pair in the request should be in ready state.
     *
     * At the completion of this operation all remote replication pairs specified in the request should
     * be in the following state:
     *     Pair state:ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     Data from R2 element is restored to R1 element.
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     Replication link on device should be in ready state;
     *
     * @param replicationPairs: remote replication pairs to failback
     * @param context: context information for this operation
     * @param capabilities storage capabilities for this operation
     * @return driver task
     */
    public DriverTask failback(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                               StorageCapabilities capabilities);

    /**
     * Swap remote replication link for remote replication pairs.
     * Changes roles of replication elements in each replication pair.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * At the completion of this operation all remote replication pairs specified in the request should
     * be in the following state:
     *     Pair state: ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R2 element is synchronized with new R1 data (R1 data is copied to R2);
     *     Original R1 element should be read enabled/write disabled;
     *     Original R2 element should be read/write enabled;
     *     R1 and R2 elements on device switch replication roles for all future link operations;
     *     Replication link on device should be in ready state;
     *
     * @param replicationPairs: remote replication pairs to swap
     * @param context: context information for this operation
     * @param capabilities storage capabilities for this operation
     * @return driver task
     */
    public DriverTask swap(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                           StorageCapabilities capabilities);

    /**
     * Changes remote replication mode for all specified pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * @param replicationPairs: remote replication pairs for mode change
     * @param newReplicationMode:  new replication mode
     * @param context: context information for this operation
     * @param capabilities storage capabilities for this operation
     * @return driver task
     */
    public DriverTask changeReplicationMode(List<RemoteReplicationPair> replicationPairs, String newReplicationMode,
                                            RemoteReplicationOperationContext context, StorageCapabilities capabilities);

    /**
     * Move replication pair from its current parent group to a different replication group parent.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     *
     * At the completion of this operation remote replication pair should be in the same state as it was before the
     * operation. The only change should be that the pair changed its parent replication group.
     *
     * @param replicationPair replication pair to move
     * @param targetGroup new parent replication group for the pair
     * @param capabilities storage capabilities for this operation
     * @return driver task
     */
    public DriverTask movePair(RemoteReplicationPair replicationPair, RemoteReplicationGroup targetGroup,
                               StorageCapabilities capabilities);
}
