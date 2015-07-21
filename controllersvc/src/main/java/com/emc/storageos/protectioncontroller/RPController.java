/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.protectioncontroller;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;

import java.net.URI;
import java.util.List;

/**
 * RecoverPoint Controller
 */
public interface RPController extends ProtectionController {  
    /**
     * Perform protection operation
     *
     * @param protectionDevice RP protection system URI
     * @param id volume ID
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
     * @param protectionDevice RP protection system URI
     * @param storageDevice storage device of the volume
     * @param snapshotList list of snapshots
     * @param createInactive (unused)
     * @param task task ID
     *
     * @throws InternalException
     */
    public void createSnapshot(URI protectionDevice, URI storageDevice, List<URI> snapshotList,
                               Boolean createInactive, String task) throws InternalException;

    /**
     * Restore an RP bookmark. This will enable the specified bookmark on the CG if the CG is not already enabled. This step is
     * required for RP bookmark restores.
     * @param protectionDevice RP protection system URI
     * @param storageDevice storage device of the volume
     * @param snapshotId snapshot URI
     * @param task task ID
     * @throws InternalException
     */
    public void restoreVolume(URI protectionDevice, URI storageDevice, URI snapshotId, String task) throws InternalException;

    /**
     * Delete a snapshot
     *
     * @param protectionDevice RP protection system URI
     * @param snapshot snap ID
     * @param task task ID
     */
    public void deleteSnapshot(URI protectionDevice, URI snapshot, String task) throws InternalException;

}
