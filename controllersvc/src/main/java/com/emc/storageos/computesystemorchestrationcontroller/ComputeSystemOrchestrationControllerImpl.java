/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemorchestrationcontroller;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class ComputeSystemOrchestrationControllerImpl implements ComputeSystemOrchestrationController {

    private Dispatcher _dispatcher;
    private ComputeSystemOrchestrationController _controller;
    private DbClient _dbClient;

    @Override
    public void mountDevice(URI hostId, URI resId, String subDirectory, String security, String mountPath, String fsType, String opId)
            throws ControllerException {

        execOrchestration("mountDevice", hostId, resId, subDirectory, security, mountPath, fsType, opId);
    }

    @Override
    public void unmountDevice(URI hostId, URI resId, String mountPath, String opId) throws ControllerException {
        execOrchestration("unmountDevice", hostId, resId, mountPath, opId);
    }

    public ComputeSystemOrchestrationController getController() {
        return _controller;
    }

    public void setController(ComputeSystemOrchestrationController controller) {
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

    private void execOrchestration(String methodName, Object... args) throws ControllerException {
        _dispatcher.queue(NullColumnValueGetter.getNullURI(), COMPUTE_SYSTEM_ORCHESTRATION_DEVICE,
                getController(), methodName, args);
    }
}
