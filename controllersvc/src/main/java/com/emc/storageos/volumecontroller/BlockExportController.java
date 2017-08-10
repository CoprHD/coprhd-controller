/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.model.ExportPathParams;

/**
 * An interface for managing the block object export operations.
 * 
 */
public interface BlockExportController extends Controller {
    String EXPORT = "export";

    /**
     * Export one or more volumes. The volumeToExports parameter has
     * all the information required to do the add volumes operation.
     * 
     * @param export URI of ExportMask
     * @param volumeMap Volume-lun map to be part of the export mask
     * @param initiatorURIs List of URIs for the initiators to be added to the export mask
     * @param opId Operation ID
     * @throws ControllerException
     */
    public void exportGroupCreate(URI export, Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs,
            String opId) throws ControllerException;

    /**
     * Update an export group
     * 
     * @param export the export group to be updated
     * @param addedBlockObjectMap (only the block objects that were added);
     * @param removedBlockObjectMap (only the block objects that were removed)
     *            can be null in which case computed from updatedBlockObjectMap
     * @param addedClusters Added Cluster URIs
     * @param removedClusters Removed Cluster URIs
     * @param addedHosts Added Host URIs
     * @param removedHosts Removed Host URIs
     * @param addedInitiators Added Initiator URIs
     * @param removedInitiators Removed Initiator URIs
     * @param opId the taskId
     * @throws ControllerException
     */
    public void exportGroupUpdate(URI export, 
            Map<URI, Integer> addedBlockObjectMap, Map<URI, Integer> removedBlockObjectMap,
            Set<URI> addedClusters, Set<URI> removedClusters, Set<URI> addedHosts,
            Set<URI> removedHosts, Set<URI> addedInitiators, Set<URI> removedInitiators, String opId) throws ControllerException;

    /**
     * Delete the export.
     * 
     * @param export URI of ExportMask
     * @param opId Operation ID
     * @throws ControllerException
     */
    public void exportGroupDelete(URI export, String opId) throws ControllerException;

    /**
     * Updates the export paths according to new path parameters in the Volume's VirtualPool.
     * This is done for all ExportMasks containing the volume if needed.
     * 
     * @param volumeURI - URI of BlockObject
     * @param newVpoolURI - URI of the new VPool (not updated yet in volume)
     * @param opId - the taskId
     * @throws ControllerException
     */
    public void updateVolumePathParams(URI volumeURI, URI newVpoolURI, String opId) throws ControllerException;

    /**
     * Updates the Auto-tiering policy and/or host io limits (for VMAX only) according to new one associated to the new VirtualPool.
     * This is done for all ExportMasks containing the volume if needed.
     * 
     * @param volumeURI - URI of BlockObject
     * @param newVpoolURI - URI of the new VPool (not updated yet in volume)
     * @param opId - the taskId
     * @throws ControllerException
     */
    public void updatePolicyAndLimits(List<URI> volumeURIs, URI newVpoolURI, String opId) throws ControllerException;
    
    /**
     * Reallocate storage ports and update export path
     * This is done for all ExportMasks that match the storage system provided 
     * and that match the varray (in case of Vplex cross connected they may not match varray)
     * and where paths were computed for the mask. (It's possible to specify as subset of all the hosts in an EG,
     * such that only certain EMs will be have paths, although this option is not supported currently in GUI.)
     * 
     * @param systemURI - URI of storage system
     * @param exportGroupURI - URI of export group
     * @param varray - URI of virtual array
     * @param addedPaths - paths to be added or retained
     * @param removedPaths - paths to be removed
     * @param exportPathParam - export path parameter
     * @param waitForApproval - if removing paths should be pending until resumed by user 
     * @param opId - the taskId
     * @throws ControllerException
     */
    public void exportGroupPortRebalance(URI systemURI, URI exportGroupURI, URI varray, Map<URI, List<URI>> addedPaths, Map<URI, List<URI>> removedPaths,
            ExportPathParams exportPathParam, boolean waitForApproval, String opId) throws ControllerException;
    
    /**
     * Change port group for existing exports. This is only valid for VMAX for now
     * 
     * @param systemURI - URI of storage system
     * @param exportGroupURI - URI of export group
     * @param newPortGroupURI - URI of new port group
     * @param waitForApproval - If remove paths should be pending until resumed by user
     * @param opId - The task id
     * @throws ControllerException
     */
    public void exportGroupChangePortGroup(URI systemURI, URI exportGroupURI, URI newPortGroupURI, boolean waitForApproval, String opId);
    
    /**
     * Updates the performance policy for the passed volumes to the passed performance policy.
     * 
     * @param volumeURIs The URIs of the volumes whose policy is to be modified.
     * @param newPerfPolicyURI The URI of the new performance policy
     * @param opId The unique task operation id.
     * 
     * @throws ControllerException
     */
    public void updatePerformancePolicy(List<URI> volumeURIs, URI newPerfPolicyURI, String opId) throws ControllerException;
}
