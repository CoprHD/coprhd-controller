package com.emc.storageos.migrationorchestrationcontroller;

import java.util.List;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class MigrationOrchestrationControllerImpl implements MigrationOrchestrationController {
    private Dispatcher _dispatcher;
    private MigrationOrchestrationController _controller;
    private DbClient _dbClient;

    @Override
    public void changeVirtualPool(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
       execOrchestration("changeVirtualPool", volumes, taskId);
    }

    @Override
    public void changeVirtualArray(List<VolumeDescriptor> volumeDescriptors, String taskId) throws ControllerException {
        execOrchestration("changeVirtualArray", volumeDescriptors, taskId);
    }

    private void execOrchestration(String methodName, Object... args) throws ControllerException {
        _dispatcher.queue(NullColumnValueGetter.getNullURI(), MIGRATION_ORCHESTRATION_DEVICE,
                getController(), methodName, args);
    }

    public MigrationOrchestrationController getController() {
        return _controller;
    }

    public void setController(MigrationOrchestrationController controller) {
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

}
