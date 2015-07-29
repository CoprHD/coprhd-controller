/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
     * 
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
     * Delete a snapshot
     * 
     * @param protectionDevice RP protection system URI
     * @param snapshot snap ID
     * @param task task ID
     */
    public void deleteSnapshot(URI protectionDevice, URI snapshot, String task) throws InternalException;

}
