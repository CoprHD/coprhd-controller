package com.emc.storageos.migrationcontroller;

import static java.lang.String.format;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MigrationEnvironmentTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MigrationOperationTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MigrationWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.vmax.VMAXRestStorageDevice;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * VMAX specific Controller implementation for non-disruptive migration (NDM).
 */
public class VMAXMigrationController implements MigrationController {
    private static final Logger logger = LoggerFactory.getLogger(VMAXMigrationController.class);

    private DbClient dbClient;
    private WorkflowService workflowService;
    private VMAXRestStorageDevice vmaxRestStorageDevice;

    private static final String MIGRATION_CREATE_ENVIRONMENT_WF_NAME = "MIGRATION_CREATE_ENVIRONMENT_WORKFLOW";
    private static final String MIGRATION_REMOVE_ENVIRONMENT_WF_NAME = "MIGRATION_REMOVE_ENVIRONMENT_WORKFLOW";
    private static final String MIGRATION_CREATE_WF_NAME = "MIGRATION_CREATE_WORKFLOW";
    private static final String MIGRATION_CUTOVER_WF_NAME = "MIGRATION_CUTOVER_WORKFLOW";
    private static final String MIGRATION_COMMIT_WF_NAME = "MIGRATION_COMMIT_WORKFLOW";
    private static final String MIGRATION_CANCEL_WF_NAME = "MIGRATION_CANCEL_WORKFLOW";
    private static final String MIGRATION_REFRESH_WF_NAME = "MIGRATION_REFRESH_WORKFLOW";
    private static final String MIGRATION_RECOVER_WF_NAME = "MIGRATION_RECOVER_WORKFLOW";
    private static final String MIGRATION_SYNCSTOP_WF_NAME = "MIGRATION_SYNCSTOP_WORKFLOW";
    private static final String MIGRATION_SYNCSTART_WF_NAME = "MIGRATION_SYNCSTART_WORKFLOW";
    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";

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

    public VMAXRestStorageDevice getVMAXRestStorageDevice() {
        return vmaxRestStorageDevice;
    }

    public void setVMAXRestStorageDevice(final VMAXRestStorageDevice vmaxRestStorageDevice) {
        this.vmaxRestStorageDevice = vmaxRestStorageDevice;
    }

    @Override
    public void migrationCreateEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException {
        logger.info("START create migration environment");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CREATE_ENVIRONMENT_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("createMigrationEnvironmentStep", "create migration environment between source system and target system",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    createMigrationEnvironmentMethod(sourceSystem, targetSystem), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully created migration environment between source system (%s) and target system (%s)",
                    sourceSystem, targetSystem);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to create migration environment between source and target systems. "
                    + "Source system: %s, target system: %s", sourceSystem, targetSystem);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationCreate(URI sourceSystem, URI cgURI, URI migrationURI, URI targetSystem, String taskId) throws ControllerException {
        logger.info("START create migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CREATE_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(Migration.class, migrationURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("createMigrationStep", "create migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    createMigrationMethod(sourceSystem, cgURI, migrationURI, targetSystem), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully created migration for consistency group %s", cgURI);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to create migration for consistency group %s", cgURI);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationCutover(URI sourceSystem, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        logger.info("START cutover migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CUTOVER_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(Migration.class, migrationURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("cutoverMigrationStep", "cutover migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    cutoverMigrationMethod(sourceSystem, cgURI, migrationURI), rollbackMethodNullMethod(), null);

            String successMessage = format("Cutover migration for consistency group %s completed successfully", cgURI);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to cutover migration for consistency group %s", cgURI);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationCommit(URI sourceSystem, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        logger.info("START commit migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_COMMIT_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(Migration.class, migrationURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("commitMigrationStep", "commit migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    commitMigrationMethod(sourceSystem, cgURI, migrationURI), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully committed migration for consistency group %s", cgURI);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to commit migration for consistency group %s", cgURI);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationCancel(URI sourceSystem, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        logger.info("START cancel migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CANCEL_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(Migration.class, migrationURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("cancelMigrationStep", "cancel migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    cancelMigrationMethod(sourceSystem, cgURI, migrationURI), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully cancelled migration for consistency group %s", cgURI);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to cancel migration for consistency group %s", cgURI);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationRefresh(URI sourceSystem, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        logger.info("START refresh migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_REFRESH_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(Migration.class, migrationURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("refreshMigrationStep", "refresh migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    refreshMigrationMethod(sourceSystem, cgURI, migrationURI), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully refreshed migration for consistency group %s", cgURI);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to refresh migration for consistency group %s", cgURI);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationRecover(URI sourceSystem, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        logger.info("START recover migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_RECOVER_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(Migration.class, migrationURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("recoverMigrationStep", "recover migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    recoverMigrationMethod(sourceSystem, cgURI, migrationURI), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully recovered migration for consistency group %s", cgURI);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to recover migration for consistency group %s", cgURI);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationSyncStop(URI sourceSystem, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        logger.info("START sync-stop migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_SYNCSTOP_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(Migration.class, migrationURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("syncStopMigrationStep", "sync-stop migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    syncStopMigrationMethod(sourceSystem, cgURI, migrationURI), rollbackMethodNullMethod(), null);

            String successMessage = format("Sync-stop migration for consistency group %s completed successfully", cgURI);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to sync-stop migration for consistency group %s", cgURI);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationSyncStart(URI sourceSystem, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        logger.info("START sync-start migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_SYNCSTART_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(Migration.class, migrationURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("syncStartMigrationStep", "sync-start migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    syncStartMigrationMethod(sourceSystem, cgURI, migrationURI), rollbackMethodNullMethod(), null);

            String successMessage = format("Sync-start migration for consistency group %s completed successfully", cgURI);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to sync-start migration for consistency group %s", cgURI);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationRemoveEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException {
        logger.info("START remove migration environment");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_REMOVE_ENVIRONMENT_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("removeMigrationEnvironmentStep", "remove migration environment between source system and target system",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    removeMigrationEnvironmentMethod(sourceSystem, targetSystem), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully removed migration environment between source system (%s) and target system (%s)",
                    sourceSystem, targetSystem);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to remove migration environment between source and target systems. "
                    + "Source system: %s, target system: %s", sourceSystem, targetSystem);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    private Workflow.Method createMigrationEnvironmentMethod(URI sourceSystem, URI targetSystem) {
        return new Workflow.Method("createMigrationEnvironment", sourceSystem, targetSystem);
    }

    public void createMigrationEnvironment(URI sourceSystem, URI targetSystem, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationEnvironmentTaskCompleter(sourceSystem, opId);
            getVMAXRestStorageDevice().
                    doCreateMigrationEnvironment(storage, targetSystem, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method removeMigrationEnvironmentMethod(URI sourceSystem, URI targetSystem) {
        return new Workflow.Method("removeMigrationEnvironment", sourceSystem, targetSystem);
    }

    public void removeMigrationEnvironment(URI sourceSystem, URI targetSystem, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationEnvironmentTaskCompleter(sourceSystem, opId);
            getVMAXRestStorageDevice().
                    doRemoveMigrationEnvironment(storage, targetSystem, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method createMigrationMethod(URI sourceSystem, URI cgURI, URI migrationURI, URI targetSystem) {
        return new Workflow.Method("createMigration", sourceSystem, cgURI, migrationURI, targetSystem);
    }

    public void createMigration(URI sourceSystem, URI cgURI, URI migrationURI, URI targetSystem, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationOperationTaskCompleter(migrationURI, opId);
            getVMAXRestStorageDevice().
                    doCreateMigration(storage, cgURI, targetSystem, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method cutoverMigrationMethod(URI sourceSystem, URI cgURI, URI migrationURI) {
        return new Workflow.Method("cutoverMigration", sourceSystem, cgURI, migrationURI);
    }

    public void cutoverMigration(URI sourceSystem, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationOperationTaskCompleter(migrationURI, opId);
            getVMAXRestStorageDevice().
                    doCutoverMigration(storage, cgURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method commitMigrationMethod(URI sourceSystem, URI cgURI, URI migrationURI) {
        return new Workflow.Method("commitMigration", sourceSystem, cgURI, migrationURI);
    }

    public void commitMigration(URI sourceSystem, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationOperationTaskCompleter(migrationURI, opId);
            getVMAXRestStorageDevice().
                    doCommitMigration(storage, cgURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method cancelMigrationMethod(URI sourceSystem, URI cgURI, URI migrationURI) {
        return new Workflow.Method("cancelMigration", sourceSystem, cgURI, migrationURI);
    }

    public void cancelMigration(URI sourceSystem, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationOperationTaskCompleter(migrationURI, opId);
            getVMAXRestStorageDevice().
                    doCancelMigration(storage, cgURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method refreshMigrationMethod(URI sourceSystem, URI cgURI, URI migrationURI) {
        return new Workflow.Method("refreshMigration", sourceSystem, cgURI, migrationURI);
    }

    public void refreshMigration(URI sourceSystem, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationOperationTaskCompleter(migrationURI, opId);
            getVMAXRestStorageDevice().
                    doRefreshMigration(storage, cgURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method recoverMigrationMethod(URI sourceSystem, URI cgURI, URI migrationURI) {
        return new Workflow.Method("recoverMigration", sourceSystem, cgURI, migrationURI);
    }

    public void recoverMigration(URI sourceSystem, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationOperationTaskCompleter(migrationURI, opId);
            getVMAXRestStorageDevice().
                    doRecoverMigration(storage, cgURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method syncStopMigrationMethod(URI sourceSystem, URI cgURI, URI migrationURI) {
        return new Workflow.Method("syncStopMigration", sourceSystem, cgURI, migrationURI);
    }

    public void syncStopMigration(URI sourceSystem, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationOperationTaskCompleter(migrationURI, opId);
            getVMAXRestStorageDevice().
                    doSyncStopMigration(storage, cgURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method syncStartMigrationMethod(URI sourceSystem, URI cgURI, URI migrationURI) {
        return new Workflow.Method("syncStartMigration", sourceSystem, cgURI, migrationURI);
    }

    public void syncStartMigration(URI sourceSystem, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new MigrationOperationTaskCompleter(migrationURI, opId);
            getVMAXRestStorageDevice().
                    doSyncStartMigration(storage, cgURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    /**
     * Creates a rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain.
     * 
     * @return A workflow method
     */
    public Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }

}
