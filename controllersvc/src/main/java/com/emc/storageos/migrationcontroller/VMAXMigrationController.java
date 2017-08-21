/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.migrationcontroller;

import static java.lang.String.format;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MigrationEnvironmentTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MigrationOperationTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MigrationWorkflowCompleter;
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
    public void migrationCreate(URI sourceSystem, URI cgURI, URI migrationURI, URI targetSystem,
            URI srp, Boolean enableCompression, String taskId) throws ControllerException {
        logger.info("START create migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_CREATE_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(BlockConsistencyGroup.class, cgURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("createMigrationStep", "create migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    createMigrationMethod(sourceSystem, cgURI, migrationURI, targetSystem, srp, enableCompression),
                    rollbackMethodNullMethod(), null);

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
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(BlockConsistencyGroup.class, cgURI, taskId);
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
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(BlockConsistencyGroup.class, cgURI, taskId);
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
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(BlockConsistencyGroup.class, cgURI, taskId);
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
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(BlockConsistencyGroup.class, cgURI, taskId);
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
    public void migrationRecover(URI sourceSystem, URI cgURI, URI migrationURI, boolean force, String taskId) throws ControllerException {
        logger.info("START recover migration");

        Workflow workflow = workflowService.getNewWorkflow(this, MIGRATION_RECOVER_WF_NAME, false, taskId);
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(BlockConsistencyGroup.class, cgURI, taskId);
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystem);
        try {
            workflow.createStep("recoverMigrationStep", "recover migration for consistency group",
                    null, sourceSystem, storage.getSystemType(), this.getClass(),
                    recoverMigrationMethod(sourceSystem, cgURI, migrationURI, force), rollbackMethodNullMethod(), null);

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
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(BlockConsistencyGroup.class, cgURI, taskId);
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
        TaskCompleter taskCompleter = new MigrationWorkflowCompleter(BlockConsistencyGroup.class, cgURI, taskId);
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

    private Workflow.Method createMigrationEnvironmentMethod(URI sourceSystemURI, URI targetSystemURI) {
        return new Workflow.Method("createMigrationEnvironment", sourceSystemURI, targetSystemURI);
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
            TaskCompleter completer = new MigrationEnvironmentTaskCompleter(sourceSystemURI, opId);
            getVmaxStorageDevice().doCreateMigrationEnvironment(sourceSystem, targetSystem, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method removeMigrationEnvironmentMethod(URI sourceSystemURI, URI targetSystemURI) {
        return new Workflow.Method("removeMigrationEnvironment", sourceSystemURI, targetSystemURI);
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
            TaskCompleter completer = new MigrationEnvironmentTaskCompleter(sourceSystemURI, opId);
            getVmaxStorageDevice().doRemoveMigrationEnvironment(sourceSystem, targetSystem, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method createMigrationMethod(URI sourceSystemURI, URI cgURI, URI migrationURI, URI targetSystemURI,
            URI srp, Boolean enableCompression) {
        return new Workflow.Method("createMigration", sourceSystemURI, cgURI, migrationURI, targetSystemURI, srp, enableCompression);
    }

    public void createMigration(URI sourceSystemURI, URI cgURI, URI migrationURI, URI targetSystemURI,
            URI srp, Boolean enableCompression, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            TaskCompleter completer = new MigrationOperationTaskCompleter(cgURI, migrationURI, opId);
            getVmaxStorageDevice().doCreateMigration(storage, cgURI, migrationURI, targetSystemURI, srp, enableCompression, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method cutoverMigrationMethod(URI sourceSystemURI, URI cgURI, URI migrationURI) {
        return new Workflow.Method("cutoverMigration", sourceSystemURI, cgURI, migrationURI);
    }

    public void cutoverMigration(URI sourceSystemURI, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            TaskCompleter completer = new MigrationOperationTaskCompleter(cgURI, migrationURI, opId);
            getVmaxStorageDevice().doCutoverMigration(storage, cgURI, migrationURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method commitMigrationMethod(URI sourceSystemURI, URI cgURI, URI migrationURI) {
        return new Workflow.Method("commitMigration", sourceSystemURI, cgURI, migrationURI);
    }

    public void commitMigration(URI sourceSystemURI, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            TaskCompleter completer = new MigrationOperationTaskCompleter(cgURI, migrationURI, opId);
            getVmaxStorageDevice().doCommitMigration(storage, cgURI, migrationURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method cancelMigrationMethod(URI sourceSystemURI, URI cgURI, URI migrationURI) {
        return new Workflow.Method("cancelMigration", sourceSystemURI, cgURI, migrationURI);
    }

    public void cancelMigration(URI sourceSystemURI, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            TaskCompleter completer = new MigrationOperationTaskCompleter(cgURI, migrationURI, opId);
            getVmaxStorageDevice().doCancelMigration(storage, cgURI, migrationURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method refreshMigrationMethod(URI sourceSystemURI, URI cgURI, URI migrationURI) {
        return new Workflow.Method("refreshMigration", sourceSystemURI, cgURI, migrationURI);
    }

    public void refreshMigration(URI sourceSystemURI, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            TaskCompleter completer = new MigrationOperationTaskCompleter(cgURI, migrationURI, opId);
            getVmaxStorageDevice().doRefreshMigration(storage, cgURI, migrationURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method recoverMigrationMethod(URI sourceSystemURI, URI cgURI, URI migrationURI, boolean force) {
        return new Workflow.Method("recoverMigration", sourceSystemURI, cgURI, migrationURI, force);
    }

    public void recoverMigration(URI sourceSystemURI, URI cgURI, URI migrationURI, boolean force, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            TaskCompleter completer = new MigrationOperationTaskCompleter(cgURI, migrationURI, opId);
            getVmaxStorageDevice().doRecoverMigration(storage, cgURI, migrationURI, force, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method syncStopMigrationMethod(URI sourceSystemURI, URI cgURI, URI migrationURI) {
        return new Workflow.Method("syncStopMigration", sourceSystemURI, cgURI, migrationURI);
    }

    public void syncStopMigration(URI sourceSystemURI, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            TaskCompleter completer = new MigrationOperationTaskCompleter(cgURI, migrationURI, opId);
            getVmaxStorageDevice().doSyncStopMigration(storage, cgURI, migrationURI, completer);
        } catch (Exception e) {
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(opId, serviceError);
        }
    }

    private Workflow.Method syncStartMigrationMethod(URI sourceSystemURI, URI cgURI, URI migrationURI) {
        return new Workflow.Method("syncStartMigration", sourceSystemURI, cgURI, migrationURI);
    }

    public void syncStartMigration(URI sourceSystemURI, URI cgURI, URI migrationURI, String opId) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, sourceSystemURI);
            TaskCompleter completer = new MigrationOperationTaskCompleter(cgURI, migrationURI, opId);
            getVmaxStorageDevice().doSyncStartMigration(storage, cgURI, migrationURI, completer);
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
