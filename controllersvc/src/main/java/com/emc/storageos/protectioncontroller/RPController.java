/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.protectioncontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * RecoverPoint Controller
 */
public interface RPController extends ProtectionController {
    /**
     * Perform protection operation
     *
     * @param protectionDevice RP protection system URI
     * @param id volume or consistency group ID
     * @param copyID id of protection volume
     * @param op operation to perform
     * @param task task object
     *
     * @throws InternalException
     */
    public void performProtectionOperation(URI protectionDevice, URI id, URI copyID, String op, String task) throws InternalException;

    /**
     * Create a snapshot or snapshots of a volume or volumes.
     * Open issue: need to allow snapshots with different VirtualPool & pool
     *
     * @param protectionDevice RP protection system URI
     * @param storageDevice storage device of the volume
     * @param snapshotList list of snapshots
     * @param createInactive (unused)
     * @param readOnly
     * @param task task ID
     *
     * @throws InternalException
     */
    public void createSnapshot(URI protectionDevice, URI storageDevice, List<URI> snapshotList,
            Boolean createInactive, Boolean readOnly, String task) throws InternalException;

    /**
     * Delete a snapshot
     *
     * @param protectionDevice RP protection system URI
     * @param snapshot snap ID
     * @param task task ID
     */
    public void deleteSnapshot(URI protectionDevice, URI snapshot, String task) throws InternalException;

}
