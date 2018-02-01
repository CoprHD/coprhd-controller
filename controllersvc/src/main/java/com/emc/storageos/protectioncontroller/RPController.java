/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.protectioncontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ApplicationAddVolumeList;

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
     * @param pointInTime any point in time in UTC.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime in milliseconds.
     * @param imageAccessMode the image access mode for the failover copy (RecoverPoint only)
     * @param op operation to perform
     * @param task task object
     *
     * @throws InternalException
     */
    public void performProtectionOperation(URI protectionDevice, URI id, URI copyID, String pointInTime, String imageAccessMode, String op,
            String task) throws InternalException;

    /**
     * Update consistency group policy.
     *
     * @param protectionDevice RP protection system URI
     * @param consistencyGroup RP consistency group URI
     * @param volumeURIs the volume URIs corresponding to the CG being updated
     * @param newVpoolURI id of the new virtual pool
     * @param task task object
     *
     * @throws InternalException
     */
    public void updateConsistencyGroupPolicy(URI protectionDevice, URI consistencyGroup, List<URI> volumeURIs, URI newVpoolURI,
            String task) throws InternalException;

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

    /**
     * adds and removes RecoverPoint protected volumes to and from applications
     *
     * @param systemURI
     * @param addVolumesNotInCG
     * @param removeVolumesURI
     * @param applicationId
     * @param taskId
     */
    public void updateApplication(URI systemURI, ApplicationAddVolumeList addVolumesNotInCG, List<URI> removeVolumesURI, URI applicationId,
            String taskId);

    /**
     * Finds the RecoverPoint copy access states for all copies associated to the volumes provided.
     *
     * @param protectionSystem the protection system to use
     * @param volumeURIs the volumes corresponding to the copies that need to be queried for access state
     * @return a mapping of volumes to access states
     */
    public Map<URI, String> getCopyAccessStates(URI protectionSystem, List<URI> volumeURIs);
}
