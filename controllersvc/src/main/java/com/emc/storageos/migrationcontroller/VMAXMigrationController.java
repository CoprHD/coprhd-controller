/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.migrationcontroller;

import static java.lang.String.format;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SimpleTaskCompleter;
import com.emc.storageos.volumecontroller.impl.vmax.VMAXStorageDevice;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * VMAX specific Controller implementation for non-disruptive migration (NDM).
 */
public class VMAXMigrationController implements MigrationController {
    private static final Logger logger = LoggerFactory.getLogger(VMAXMigrationController.class);

    private DbClient dbClient;
    private WorkflowService workflowService;
    private VMAXStorageDevice vmaxStorageDevice;

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

    public VMAXStorageDevice getVmaxStorageDevice() {
        return vmaxStorageDevice;
    }

    public void setVmaxStorageDevice(final VMAXStorageDevice vmaxStorageDevice) {
        this.vmaxStorageDevice = vmaxStorageDevice;
    }

    @Override
    public void migrationCreateEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException {
        logger.info("START create migration environment");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CREATE_ENVIRONMENT_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId); // TODO use right completer
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
    public void migrationCreate(URI sourceSystem, URI cgId, URI targetSystem, String taskId) throws ControllerException {
        logger.info("START create migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CREATE_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("createMigrationStep", "create migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    createMigrationMethod(sourceSystem, cgId, targetSystem), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully created migration for consistency group %s", cgId);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to create migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationCutover(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        logger.info("START cutover migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CUTOVER_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("cutoverMigrationStep", "cutover migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    cutoverMigrationMethod(sourceSystem, cgId), rollbackMethodNullMethod(), null);

            String successMessage = format("Cutover migration for consistency group %s completed successfully", cgId);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to cutover migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationCommit(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        logger.info("START commit migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_COMMIT_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("commitMigrationStep", "commit migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    commitMigrationMethod(sourceSystem, cgId), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully committed migration for consistency group %s", cgId);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to commit migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationCancel(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        logger.info("START cancel migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CANCEL_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("cancelMigrationStep", "cancel migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    cancelMigrationMethod(sourceSystem, cgId), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully cancelled migration for consistency group %s", cgId);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to cancel migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationRefresh(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        logger.info("START refresh migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_REFRESH_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("refreshMigrationStep", "refresh migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    refreshMigrationMethod(sourceSystem, cgId), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully refreshed migration for consistency group %s", cgId);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to refresh migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationRecover(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        logger.info("START recover migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_RECOVER_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("recoverMigrationStep", "recover migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    recoverMigrationMethod(sourceSystem, cgId), rollbackMethodNullMethod(), null);

            String successMessage = format("Successfully recovered migration for consistency group %s", cgId);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to recover migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationSyncStop(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        logger.info("START sync-stop migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_SYNCSTOP_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("syncStopMigrationStep", "sync-stop migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    syncStopMigrationMethod(sourceSystem, cgId), rollbackMethodNullMethod(), null);

            String successMessage = format("Sync-stop migration for consistency group %s completed successfully", cgId);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to sync-stop migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationSyncStart(URI sourceSystem, URI cgId, String taskId) throws ControllerException {
        logger.info("START sync-start migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_SYNCSTART_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("syncStartMigrationStep", "sync-start migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    syncStartMigrationMethod(sourceSystem, cgId), rollbackMethodNullMethod(), null);

            String successMessage = format("Sync-start migration for consistency group %s completed successfully", cgId);
            workflow.executePlan(taskCompleter, successMessage);
        } catch (Exception e) {
            String errorMsg = format("Failed to sync-start migration for consistency group %s", cgId);
            logger.error(errorMsg, e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    @Override
    public void migrationRemoveEnvironment(URI sourceSystem, URI targetSystem, String taskId) throws ControllerException {
        logger.info("START remove migration environment");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_REMOVE_ENVIRONMENT_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, taskId);
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

    /**
     * Create migration environment
     *
     * @param sourceSystemURI
     * @param targetSystemURI
     * @param opId
     * @throws ControllerException
     */
    public void createMigrationEnvironment(URI sourceSystemURI, URI targetSystemURI, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, targetSystemURI);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystemURI, opId);
            getVmaxStorageDevice().doCreateMigrationEnvironment(sourceSystem, targetSystem, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method removeMigrationEnvironmentMethod(URI sourceSystem, URI targetSystem) {
        return new Workflow.Method("removeMigrationEnvironment", sourceSystem, targetSystem);
    }

    /**
     * Remove migration environment
     *
     * @param sourceSystemURI
     * @param targetSystemURI
     * @param opId
     * @throws ControllerException
     */
    public void removeMigrationEnvironment(URI sourceSystemURI, URI targetSystemURI, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, targetSystemURI);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystemURI, opId);
            getVmaxStorageDevice().doRemoveMigrationEnvironment(sourceSystem, targetSystem, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method createMigrationMethod(URI sourceSystem, URI cgId, URI targetSystem) {
        return new Workflow.Method("createMigration", sourceSystem, cgId, targetSystem);
    }

    public void createMigration(URI sourceSystem, URI cgId, URI targetSystem, String opId)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, opId);
            getVmaxStorageDevice().
                    doCreateMigration(storage, cgId, targetSystem, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method cutoverMigrationMethod(URI sourceSystem, URI cgId) {
        return new Workflow.Method("cutoverMigration", sourceSystem, cgId);
    }

    public void cutoverMigration(URI sourceSystem, URI cgId, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, opId);
            getVmaxStorageDevice().
                    doCutoverMigration(storage, cgId, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method commitMigrationMethod(URI sourceSystem, URI cgId) {
        return new Workflow.Method("commitMigration", sourceSystem, cgId);
    }

    public void commitMigration(URI sourceSystem, URI cgId, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, opId);
            getVmaxStorageDevice().
                    doCommitMigration(storage, cgId, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method cancelMigrationMethod(URI sourceSystem, URI cgId) {
        return new Workflow.Method("cancelMigration", sourceSystem, cgId);
    }

    public void cancelMigration(URI sourceSystem, URI cgId, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, opId);
            getVmaxStorageDevice().
                    doCancelMigration(storage, cgId, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method refreshMigrationMethod(URI sourceSystem, URI cgId) {
        return new Workflow.Method("refreshMigration", sourceSystem, cgId);
    }

    public void refreshMigration(URI sourceSystem, URI cgId, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, opId);
            getVmaxStorageDevice().
                    doRefreshMigration(storage, cgId, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method recoverMigrationMethod(URI sourceSystem, URI cgId) {
        return new Workflow.Method("recoverMigration", sourceSystem, cgId);
    }

    public void recoverMigration(URI sourceSystem, URI cgId, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, opId);
            getVmaxStorageDevice().
                    doRecoverMigration(storage, cgId, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method syncStopMigrationMethod(URI sourceSystem, URI cgId) {
        return new Workflow.Method("syncStopMigration", sourceSystem, cgId);
    }

    public void syncStopMigration(URI sourceSystem, URI cgId, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, opId);
            getVmaxStorageDevice().
                    doSyncStopMigration(storage, cgId, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method syncStartMigrationMethod(URI sourceSystem, URI cgId) {
        return new Workflow.Method("syncStartMigration", sourceSystem, cgId);
    }

    public void syncStartMigration(URI sourceSystem, URI cgId, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
            TaskCompleter completer = new SimpleTaskCompleter(StorageSystem.class, sourceSystem, opId);
            getVmaxStorageDevice().
                    doSyncStartMigration(storage, cgId, completer);
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

    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

}
