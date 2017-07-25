package com.emc.storageos.migrationcontroller;

import java.net.URI;
import java.util.Set;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class MigrationControllerImpl extends AbstractDiscoveredSystemController implements MigrationController {
    private Dispatcher dispatcher;
    private DbClient dbClient;
    private Set<MigrationController> deviceImpl;

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public Set<MigrationController> getDeviceImpl() {
        return deviceImpl;
    }

    public void setDeviceImpl(Set<MigrationController> deviceImpl) {
        this.deviceImpl = deviceImpl;
    }

    @Override
    protected Controller lookupDeviceController(DiscoveredSystemObject storageSystem) {
        return deviceImpl.iterator().next();
    }

    /**
     * Puts the operation in the zkQueue so it can dispatched to a Device Controller.
     * 
     * @param methodName
     * @param args
     * @throws InternalException
     */
    private void blockRMI(String methodName, Object... args) throws InternalException {
        queueTask(dbClient, StorageSystem.class, dispatcher, methodName, args);
    }

    @Override
    public void migrationCreateEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException {
        blockRMI("migrationCreateEnvironment", sourceSystem, targetSystem, taskId);
    }

    @Override
    public void migrationCreate(URI sourceSystem, URI cgId, URI targetSystem, String taskId) throws ControllerException {
        blockRMI("migrationCreate", sourceSystem, cgId, targetSystem, taskId);
    }

    @Override
    public void migrationCutover(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        blockRMI("migrationCutover", sourceSystem, cgId, taskId);
    }

    @Override
    public void migrationCommit(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        blockRMI("migrationCommit", sourceSystem, cgId, taskId);
    }

    @Override
    public void migrationCancel(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        blockRMI("migrationCancel", sourceSystem, cgId, taskId);
    }

    @Override
    public void migrationRefresh(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        blockRMI("migrationRefresh", sourceSystem, cgId, taskId);
    }

    @Override
    public void migrationRecover(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        blockRMI("migrationRecover", sourceSystem, cgId, taskId);
    }

    @Override
    public void migrationSyncStop(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        blockRMI("migrationSyncStop", sourceSystem, cgId, taskId);
    }

    @Override
    public void migrationSyncStart(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        blockRMI("migrationSyncStart", sourceSystem, cgId, taskId);
    }

    @Override
    public void migrationRemoveEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException {
        blockRMI("migrationRemoveEnvironment", sourceSystem, targetSystem, taskId);
    }

}
