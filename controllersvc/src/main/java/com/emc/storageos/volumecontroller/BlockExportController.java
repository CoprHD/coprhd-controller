/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.Controller;

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
     * can be null in which case computed from updatedBlockObjectMap
     * @param updatedClusters the updated list of clusters
     * @param updatedHosts the updated list of hosts
     * @param updatedInitiators the updates list of initiators
     * @param opId the taskId
     * @throws ControllerException
     */
    public void exportGroupUpdate(URI export, 
            Map<URI, Integer> addedBlockObjectMap,
            Map<URI,  Integer> removedBlockObjectMap,
            List<URI> updatedClusters,
            List<URI> updatedHosts,
            List<URI> updatedInitiators,
            String opId) throws ControllerException;

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
}
