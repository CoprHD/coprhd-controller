/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remotereplicationcontroller;


import java.net.URI;
import java.util.List;

import com.emc.storageos.Controller;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

public interface RemoteReplicationController extends Controller {

    /**
     * Create empty remote replication group.
     * @param replicationGroup URI of remote replication group to create.
     */
    public void createRemoteReplicationGroup(URI replicationGroup, List<URI> sourcePorts, List<URI> targetPorts, String opId);

    /**
     * Create replication pairs in existing replication group container.
     * At the completion of this operation all remote replication pairs should be associated to their group and should
     * be in the following state:
     *  a) createActive is true:
     *     Pairs state: ACTIVE;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data copied to R2;
     *     replication links are set to ready state;
     *
     *  b) createActive is false:
     *     Pairs state: SPLIT;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links are set to not ready state;
     *     The state of replication pairs is same as after 'split' operation.
     *
     * @param replicationPairs list of replication pairs URI to create
     */
    public void createGroupReplicationPairs(List<URI> replicationPairs, String opId);

    /**
     * Create replication pairs in existing replication set. Pairs are created outside of group container.
     * At the completion of this operation all remote replication pairs should be associated to their set and should
     * be in the following state:
     *  a) createActive is true:
     *     Pairs state: ACTIVE;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data copied to R2;
     *     replication links are set to ready state;
     *
     *  b) createActive is false:
     *     Pairs state: SPLIT;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links are set to not ready state;
     *     The state of replication pairs is same as after 'split' operation.
     *
     * @param replicationPairs list of replication pairs to create
     */
    public void createSetReplicationPairs(List<URI> replicationPairs, String opId);

    /**
     * Delete remote replication pairs. Should not delete backend volumes.
     * Only should affect remote replication configuration on array.
     * At the completion of this operation all remote replication elements from the pairs
     * should be in the following state:
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links are not ready;
     *     R1 and R2 elements are disassociated from their remote replication containers and
     *     become independent storage elements;
     *
     * @param replicationPairs replication pairs to delete
     */
    public void deleteReplicationPairs(List<URI> replicationPairs, String opId);


    // replication link operations
    /**
     * Suspend remote replication link for remote replication element.
     *
     * Before this operation all remote replication pairs which belong to the replication element
     * should be in ACTIVE state.
     *
     * At the completion of this operation all remote replication pairs which belong to the replication element should
     * be in the following state:
     *     Pairs state: SUSPENDED;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     replication links should be in not ready state;
     *
     * @param replicationElement: set/group/pair
     */
    public void suspend(RemoteReplicationElement replicationElement, String opId);

    /**
     * Resume remote replication link for remote replication element.
     *
     * Before this operation all remote replication pairs which belong to the replication element
     * should be in SUSPENDED state.
     *
     *
     * At the completion of this operation all remote replication pairs which belong to the replication element should
     * be in the following state:
     *     Pairs state: ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data copied to R2 elements;
     *     replication links should be in ready state;
     *
     * @param replicationElement: set/group/pair
     */
    public void resume(RemoteReplicationElement replicationElement, String opId);

    /**
     * Split remote replication link for remote replication element.
     *
     * Before this operation all remote replication pairs which belong to the replication element
     * should be in ACTIVE state.
     *
     * At the completion of this operation all remote replication pairs which belong to the replication element should
     * be in the following state:
     *     Pairs state: SPLIT;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links should be in not ready state;
     *
     * @param replicationElement: set/group/pair
     */
    public void split(RemoteReplicationElement replicationElement, String opId);

    /**
     * Establish replication link for remote replication element.
     *
     * Before this operation all remote replication pairs which belong to the replication element
     * should be in SPLIT state.
     *
     * At the completion of this operation all remote replication pairs which belong to the replication element should
     * be in the following state:
     *     Pairs state: ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data;
     *     replication links should be in ready state;
     *
     * @param replicationElement replication element: set/group/pair
     */
    public void establish(RemoteReplicationElement replicationElement, String opId);

    /**
     * Failover remote replication link for remote replication element.
     *
     * Before this operation all remote replication pairs which belong to the replication element
     * should be in ACTIVE state.
     *
     * At the completion of this operation all remote replication pairs which belong to the replication element should
     * be in the following state:
     *     Pairs state: FAILED_OVER;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be write disabled;
     *     R2 elements should be read/write enabled;
     *     replication links should be in not ready state;
     *
     * @param replicationElement: set/group/pair
     */
    public void failover(RemoteReplicationElement replicationElement, String opId);

    /**
     * Failback (restore) remote replication link for remote replication element.
     *
     * Before this operation all remote replication pairs which belong to the replication element
     * should be in FAILED_OVER state.
     *
     * At the completion of this operation all remote replication pairs which belong to the replication element should
     * be in the following state:
     *     Pairs state:ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be write disabled;
     *     data on R1 elements is synchronized with new R2 data copied to R1;
     *     replication links should be in ready state;
     *
     * @param replicationElement: set/group/pair
     */
    public void failback(RemoteReplicationElement replicationElement, String opId);

    /**
     * Swap remote replication link for remote replication element.
     * Changes roles of replication elements in each replication pair.
     *
     * Before this operation all remote replication pairs which belong to the replication element
     * should be in ACTIVE state.
     *
     * At the completion of this operation all remote replication pairs which belong to the replication element should
     * be in the following state:
     *     Pairs state: ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read enabled/write disabled;
     *     R2 elements should be read/write enabled;
     *     data on R2 elements is synchronized with new R1 data copied to R2;
     *     replication links should be in ready state for replication from R2 to R1;
     *     SWAP operation should reverse pair's replication direction and R1/R2 roles;
     *
     * @param replicationElement: set/group/pair
     */
    public void swap(RemoteReplicationElement replicationElement, String opId);

    /**
     * Move replication pair from its parent group to other replication group.
     * At the completion of this operation remote replication pair should be in the same state as it was before the
     * operation. The only change should be that the pair changed its parent replication group.
     *
     * @param replicationPair replication pair to move
     * @param targetGroup new parent replication group for the pair
     */
    public void movePair(URI replicationPair, URI targetGroup, String opId);


    /**
     * Change remote replication mode for the specified remote replication element.
     *
     * @param replicationElement replication element
     * @param newRemoteReplicationMode
     * @param opId
     */
    public void changeReplicationMode(RemoteReplicationElement replicationElement, String newRemoteReplicationMode, String opId);
}
