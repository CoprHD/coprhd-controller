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
 * RemoteReplicationContext parameter, defined in the remote replication link operations, specifies remote replication
 * container for which link operation was initiated by the controller. For example, if system operation was executed for remote replication group,
 * the context will specify group element type, native id of the group replication set and native id of the group.
 * Other example, if controller operation was executed for individual pair directly, the context will specify pair element type.
 * In this case native ids of remote replication set and group are set to native ids of remote replication set and
 * remote replication group (if applicable) of individual pair.
 *
 *
 * Driver may use RemoteReplicationContext parameter to check validity of remote replication link operation. For example, if request has
 * remote replication group context and only subset of remote replication pairs from the remote replication group in the system are specified
 * (this indicates that controller has stale information about system configuration), driver may fail this request.
 * When remote link operation executed with container context type (remote replication group or remote replication set), it
 * is driver responsibility to update state of containers as required by device support for link operations.
 *
 * R1 and R2 are roles of remote replication pair source and target elements based on direction of replication link.
 * R1 is a replication source role, R2 is replication target role.
 * Replication pair source and target elements can have either role depending on direction of replication link between them.
 *
 * Driver is free to implement remote replication link management methods in this interface class
 * based on its storage domain specification for disaster recovery operations.
 * "REFERENCE RECOMMENDATION FOR IMPLEMENTATION" section in methods documentation below is for reference purpose only.
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
     * At the completion of this operation each created remote replication pair on device should be associated to its group.
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     * If request is to create active pairs (defined in capabilities parameter):
     *     Pair state: ACTIVE;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     data on R2 element is synchronized with R1 data (R1 data is copied to R2);
     *     replication link between R1 and R2 elements on device is set to ready state;
     *
     * If request is to create inactive pairs (defined in capabilities parameter):
     *     Pair state: SPLIT;
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
     * At the completion of this operation each created remote replication pair on device should be associated to its set.
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     * If request is to create active pairs (defined in capabilities parameter):
     *     Pair state: ACTIVE;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     data on R2 element is synchronized with R1 data (R1 data is copied to R2);
     *     replication link between R1 and R2 elements on device is set to ready state;
     *
     * If request is to create inactive pairs (defined in capabilities parameter):
     *     Pair state: SPLIT;
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
     * in the request. No change in remote replication container (group/set) for the pairs.
     * If execution of the request with this constraint is not possible, should return a failure.
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     *    Before this operation replication link for each pair in the request should be in ready state.
     *
     *    At the completion of this operation all remote replication pairs should be in the following state:
     *     Pair state: SUSPENDED;
     *     Replication link on device should be in not ready state;
     *     Data on R2 element should be synchronized with data on R1 element
     *     prior to operation according to replication mode requirements;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
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
     * in the request. No change in remote replication container (group/set) for the pairs.
     * If execution of the request with this constraint is not possible, should return a failure.
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     *   At the completion of this operation all remote replication pairs specified in the request should
     *   be in the following state:
     *     Pair state: ACTIVE;
     *     Replication link on device should be in ready state;
     *     Data on R2 elements is synchronized with R1 data (R1 data copied to R2);
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
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
     * in the request. No change in remote replication container (group/set) for the pair.
     * If execution of the request with this constraint is not possible, should return a failure.
     *
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     *   Before this operation replication link for each pair in the request should be in ready state.
     *
     *   At the completion of this operation all remote replication pairs specified in the request should
     *   be in the following state:
     *     Pair state: SPLIT;
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
     * in the request. No change in remote replication container (group/set) for the pair.
     * If execution of the request with this constraint is not possible, should return a failure.
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     *   This operation starts replication links for replication pairs on device.
     *
     *   At the completion of this operation all remote replication pairs specified in the request should
     *   be in the following state:
     *     Pair state: ACTIVE;
     *     Replication link on device should be in ready state;
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
     *     Data on R2 element is synchronized with R1 data(R1 data copied to R2);
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
     * in the request. No change in remote replication container (group/set) for the pairs.
     * If execution of the request with this constraint is not possible, should return a failure.
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     *   At the completion of this operation all remote replication pairs specified in the request should
     *   be in the following state:
     *     Pair state: FAILED_OVER;
     *     Replication links should be in not ready state;
     *     Data on R2 element should be synchronized with data on R1 element
     *     prior to operation according to replication mode requirements;
     *     R2 element should be read/write enabled;
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
     * in the request. No change in remote replication container (group/set) for the pairs.
     * If execution of the request with this constraint is not possible, should return a failure.
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     *   Before this operation replication link for each pair in the request should be in ready state.
     *
     *   At the completion of this operation all remote replication pairs specified in the request should
     *   be in the following state:
     *     Pair state:ACTIVE;
     *     Replication link on device should be in ready state;
     *     Data from R2 element is restored to R1 element.
     *     R1 element should be read/write enabled;
     *     R2 element should be read enabled/write disabled;
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
     * Should not make any impact to replication state of any other existing replication pairs which are not specified
     * in the request. No change in remote replication container (group/set) for the pairs.
     * If execution of the request with this constraint is not possible, should return a failure.
     *
     * Changes replication direction and element roles in each replication pair.
     * Original R1 gets R2 role and original R2 gets R1 role.
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     *   At the completion of this operation all remote replication pairs specified in the request should
     *   be in the following state:
     *     Pair state: SWAPPED/ACTIVE;
     *     Replication link on device should be in ready state;
     *     Original R2 element is synchronized with new R1 data (R1 data is copied to R2);
     *     Original R1 element should be read enabled/write disabled;
     *     Original R2 element should be read/write enabled;
     *     Replication direction is from original R2 element to original R1 element;
     *
     *
     * @param replicationPairs: remote replication pairs to swap
     * @param context: context information for this operation
     * @param capabilities storage capabilities for this operation
     * @return driver task
     */
    public DriverTask swap(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                           StorageCapabilities capabilities);


    /**
     * Stop remote replication link for remote replication pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request.
     * If execution of the request with this constraint is not possible, should return a failure.
     *
     *
     * REFERENCE RECOMMENDATION FOR IMPLEMENTATION:
     *   Before this operation replication link for each pair in the request should be in split state.
     *
     *   At the completion of this operation R1 and R2 elements in each of remote replication pairs specified in this request
     *   should become independent local read/write enabled storage elements on arrays.
     *   Any operation executed on storage elements after remote replication link "stop" should produce the same result as
     *   if it was executed on local storage element without remote replication.
     *
     *
     * @param replicationPairs: remote replication pairs to stop
     * @param capabilities storage capabilities for this operation
     * @param context: context information for this operation
     * @return driver task
     */
    public DriverTask stop(List<RemoteReplicationPair> replicationPairs, RemoteReplicationOperationContext context,
                            StorageCapabilities capabilities);

    /**
     * Changes remote replication mode for all specified pairs.
     * Should not make any impact on replication state of any other existing replication pairs which are not specified
     * in the request. If execution of the request with this constraint is not possible, should return a failure.
     * This operation should not affect replication state of remote replication element.
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
