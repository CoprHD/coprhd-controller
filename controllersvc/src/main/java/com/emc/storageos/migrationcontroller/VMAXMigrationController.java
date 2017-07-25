package com.emc.storageos.migrationcontroller;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.workflow.WorkflowService;

/**
 * VMAX specific Controller implementation for non-disruptive migration (NDM).
 */
public class VMAXMigrationController implements MigrationController {
    private static final Logger logger = LoggerFactory.getLogger(VMAXMigrationController.class);

    private DbClient dbClient;
    private WorkflowService workflowService;
    private Map<String, BlockStorageDevice> devices;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(final DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public WorkflowService getWorkflowService() {
        return workflowService;
    }

    public void setWorkflowService(final WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public Map<String, BlockStorageDevice> getDevices() {
        return devices;
    }

    public void setDevices(final Map<String, BlockStorageDevice> devices) {
        this.devices = devices;
    }

    @Override
    public void migrationCreateEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationCreate(URI sourceSystem, URI cgId, URI targetSystem, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationCutover(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationCommit(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationCancel(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationRefresh(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationRecover(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationSyncStop(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationSyncStart(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrationRemoveEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException {
        // TODO Auto-generated method stub

    }

}
