/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.blockorchestrationcontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class BlockOrchestrationControllerImpl implements BlockOrchestrationController {
    private Dispatcher _dispatcher;
    private BlockOrchestrationController _controller;
    private DbClient _dbClient;

    @Override
    public void createVolumes(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException {
        execOrchestration("createVolumes", volumes, taskId);
    }

    @Override
    public void deleteVolumes(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException {
        execOrchestration("deleteVolumes", volumes, taskId);
    }

    @Override
    public void expandVolume(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException {
        execOrchestration("expandVolume", volumes, taskId);

    }

    @Override
    public void restoreVolume(URI storage, URI pool, URI volume,
            URI snapshot, String syncDirection, String taskId) throws ControllerException {
        execOrchestration("restoreVolume", storage, pool, volume, snapshot, syncDirection, taskId);
    }

    @Override
    public void changeVirtualPool(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException {
        execOrchestration("changeVirtualPool", volumes, taskId);
    }

    @Override
    public void changeVirtualArray(List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws ControllerException {
        execOrchestration("changeVirtualArray", volumeDescriptors, taskId);
    }

    private void execOrchestration(String methodName, Object... args) throws ControllerException {
        _dispatcher.queue(NullColumnValueGetter.getNullURI(), BLOCK_ORCHESTRATION_DEVICE,
                getController(), methodName, args);
    }

    public BlockOrchestrationController getController() {
        return _controller;
    }

    public void setController(BlockOrchestrationController controller) {
        this._controller = controller;
    }

    public Dispatcher getDispatcher() {
        return _dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this._dispatcher = dispatcher;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    @Override
    public void restoreFromFullCopy(URI storage, List<URI> fullCopyURIs, String opId) throws InternalException {
        execOrchestration("restoreFromFullCopy", storage, fullCopyURIs, opId);
        
    }
}
