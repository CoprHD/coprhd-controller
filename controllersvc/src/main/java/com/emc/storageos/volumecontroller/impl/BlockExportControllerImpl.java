/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;

public class BlockExportControllerImpl implements BlockExportController {
    private static final Logger _log = LoggerFactory.getLogger(BlockExportControllerImpl.class);
    private Set<BlockExportController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;

    public void setDeviceImpl(Set<BlockExportController> deviceImpl) {
        _deviceImpl = deviceImpl;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Dummy implementation that just returns the first controller that implements this device
     * 
     * @return
     * @throws com.emc.storageos.volumecontroller.ControllerException
     * 
     */
    private BlockExportController getExportController()
            throws ControllerException {
        BlockExportController bc = _deviceImpl.iterator().next();
        if (bc == null) {
            throw ClientControllerException.fatals.unableToLocateDeviceController("block controller");
        }
        return bc;
    }

    /**
     * Puts the operation in the zkQueue so it can Dispatch'd to a Device Controller
     * 
     * @param methodName
     * @param args
     * @throws com.emc.storageos.volumecontroller.ControllerException
     * 
     */
    private void blockRMI(String methodName, Object... args) throws ControllerException {
        final URI export = (URI) args[0];
        BlockExportController controller = getExportController();
        _dispatcher.queue(export, "export", controller, methodName, args);
    }

    /**
     * Export one or more volumes. The volumeToExports parameter has
     * all the information required to do the add volumes operation.
     * 
     * @param export URI of ExportMask
     * @param volumeMap Volume-lun map to be part of the export mask
     * @param initiatorURIs List of URIs for the initiators to be added to the export mask
     * @param opId Operation ID
     * @throws com.emc.storageos.volumecontroller.ControllerException
     * 
     */
    @Override
    public void exportGroupCreate(URI export, Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs, String opId)
            throws ControllerException {
        blockRMI("exportGroupCreate", export, volumeMap, initiatorURIs, opId);
    }

    /**
     * Delete the export.
     * 
     * @param export URI of ExportMask
     * @param opId Operation ID
     * @throws com.emc.storageos.volumecontroller.ControllerException
     * 
     */
    @Override
    public void exportGroupDelete(URI export, String opId) throws ControllerException {
        blockRMI("exportGroupDelete", export, opId);
    }

    @Override
    public void exportGroupUpdate(URI export, 
            Map<URI, Integer> addedBlockObjectMap, Map<URI, Integer> removedObjectMap, 
            Set<URI> addedClusters, Set<URI> removedClusters,
            Set<URI> addedHosts, Set<URI> removedHosts, Set<URI> addedInitiators, Set<URI> removedInitiators, String opId)
            throws ControllerException {
        blockRMI("exportGroupUpdate", export, addedBlockObjectMap, removedObjectMap,
                addedClusters, removedClusters, addedHosts, removedHosts, addedInitiators,
                removedInitiators, opId);
    }

    /**
     * Update the path parameters for a volume from a new Vpool.
     * 
     * @param volumeURI - URI of volume
     * @param newVpoolURI - URI of new Vpool
     * @param opid String containing task identifier
     */
    @Override
    public void updateVolumePathParams(URI volumeURI, URI newVpoolURI, String opId) throws ControllerException {
        blockRMI("updateVolumePathParams", volumeURI, newVpoolURI, opId);
    }

    /**
     * Updates the Auto-tiering policy from a new vPool for the given volumes.
     * 
     * @param volumeURIs the volume uris
     * @param newVpoolURI - URI of new Vpool
     * @param opId the op id
     * @throws ControllerException the controller exception
     */
    @Override
    public void updatePolicyAndLimits(List<URI> volumeURIs, URI newVpoolURI, String opId) throws ControllerException {
        BlockExportController controller = getExportController();
        _dispatcher.queue(volumeURIs.get(0), "export", controller,
                "updatePolicyAndLimits", volumeURIs, newVpoolURI, opId);
    }
    
    @Override
    public void exportGroupPortRebalance(URI systemURI, URI exportGroupURI, URI varray, Map<URI, List<URI>> addedPaths, Map<URI, List<URI>> removedPaths,
            ExportPathParams exportPathParam, boolean waitForApproval, String opId) throws ControllerException {
        blockRMI("exportGroupPortRebalance", systemURI, exportGroupURI, varray, addedPaths, removedPaths, 
                exportPathParam, waitForApproval, opId);
    }
    
    @Override
    public void exportGroupChangePortGroup(URI systemURI, URI exportGroupURI, URI newPortGroupURI, boolean waitForApproval,
            String opId) {
        blockRMI("exportGroupChangePortGroup", systemURI, exportGroupURI, newPortGroupURI, waitForApproval, opId);
    }
}
