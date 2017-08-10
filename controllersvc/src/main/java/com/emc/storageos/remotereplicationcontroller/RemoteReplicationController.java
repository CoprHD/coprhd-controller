/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remotereplicationcontroller;


import java.net.URI;
import java.util.List;

import com.emc.storageos.Controller;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

public interface RemoteReplicationController extends Controller {

    public enum RemoteReplicationOperations {
        ESTABLISH,
        SUSPEND,
        RESUME,
        SPLIT,
        FAIL_OVER,
        FAIL_BACK,
        SWAP,
        STOP,
        CHANGE_REPLICATION_MODE,
        MOVE_PAIR
    }

    /**
     * Create empty remote replication group.
     * @param replicationGroup URI of remote replication group to create.
     */
    public void createRemoteReplicationGroup(URI replicationGroup, List<URI> sourcePorts, List<URI> targetPorts, String opId);


    /**
     * Controller method to initiate driver call to delete remote replication pairs. Should not delete backend volumes.
     * Only should affect remote replication configuration on array.
     * At the completion of this operation all remote replication elements from the pairs
     * should be in the following state:
     *     source and target elements are disassociated from their remote replication containers and
     *     become independent storage elements;
     *
     * @param replicationPairs replication pairs to delete
     */
    public void deleteReplicationPairs(List<URI> replicationPairs, String opId);


    // replication link operations
    /**
     * Controller method to initiate driver call to suspend remote replication link for remote replication element.
     *
     * @param replicationElement: set/group/pair
     */
    public void suspend(RemoteReplicationElement replicationElement, String opId);

    /**
     * Controller method to initiate driver call to resume remote replication link for remote replication element.
     *
     * @param replicationElement: set/group/pair
     */
    public void resume(RemoteReplicationElement replicationElement, String opId);

    /**
     * Controller method to initiate driver call to split remote replication link for remote replication element.
     *
     * @param replicationElement: set/group/pair
     */
    public void split(RemoteReplicationElement replicationElement, String opId);

    /**
     * Controller method to initiate driver call to establish replication link for remote replication element.
     *
     * @param replicationElement replication element: set/group/pair
     */
    public void establish(RemoteReplicationElement replicationElement, String opId);

    /**
     * Controller method to initiate driver call to failover remote replication link for remote replication element.
     *
     * @param replicationElement: set/group/pair
     */
    public void failover(RemoteReplicationElement replicationElement, String opId);

    /**
     * Controller method to initiate driver call to failback (restore) remote replication link for remote replication element.
     *
     * @param replicationElement: set/group/pair
     */
    public void failback(RemoteReplicationElement replicationElement, String opId);

    /**
     * Controller method to initiate driver call to swap remote replication link for remote replication element.
     * Changes replication direction in each replication pair.
     *
     * @param replicationElement: set/group/pair
     */
    public void swap(RemoteReplicationElement replicationElement, String opId);

    /**
     * Controller method to initiate driver call to delete remote replication link for remote replication element.
     *
     * @param replicationElement: set/group/pair
     */
    public void stop(RemoteReplicationElement replicationElement, String opId);

    /**
     * Move replication pair from its parent group to other replication group.
     * At the completion of this operation remote replication pair should be in the same state as it was before the
     * operation. The only change should be that the pair changed its parent replication group.
     * As a result of parent group change, replication mode of the pair may also change.
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
