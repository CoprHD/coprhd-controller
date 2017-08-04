/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.filereplicationcontroller;

import static java.util.Arrays.asList;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationInterface;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorRollbackCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileCreateTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.RemoteFileMirrorOperation;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * FileReplicationDeviceController-specific Controller implementation with support for file Orchestration.
 */
public class FileReplicationDeviceController implements FileOrchestrationInterface, FileReplicationController {

    private static final Logger log = LoggerFactory.getLogger(FileReplicationDeviceController.class);

    private WorkflowService workflowService;
    private DbClient dbClient;
    private Map<String, FileStorageDevice> devices;

    private static final String CREATE_FILE_MIRRORS_STEP = "CREATE_FILE_MIRRORS_STEP";
    private static final String CREATE_FILE_MIRROR_PAIR_METH = "createMirrorSession";
    private static final String ROLLBACK_MIRROR_LINKS_METHOD = "rollbackMirrorFileShareStep";

    private static final String CREATE_FILE_MIRRORS_STEP_DESC = "Create MirrorFileShare Link";
    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";

    /**
     * Calls to remote mirror operations on devices
     * 
     * @param storageSystem
     * @return
     */

    private RemoteFileMirrorOperation getRemoteMirrorDevice(StorageSystem storageSystem) {
        return (RemoteFileMirrorOperation) devices.get(storageSystem.getSystemType());
    }

    public WorkflowService getWorkflowService() {
        return workflowService;
    }

    public void setWorkflowService(final WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(final DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public Map<String, FileStorageDevice> getDevices() {
        return devices;
    }

    public void setDevices(final Map<String, FileStorageDevice> devices) {
        this.devices = devices;
    }

    /**
     * Create mirror session or link between source fileshare and target fileshare
     */
    @Override
    public String addStepsForCreateFileSystems(Workflow workflow,
            String waitFor, List<FileDescriptor> filesystems, String taskId)
            throws InternalException {

        List<FileDescriptor> fileDescriptors = FileDescriptor.filterByType(filesystems,
                new FileDescriptor.FileType[] { FileDescriptor.FileType.FILE_MIRROR_SOURCE,
                        FileDescriptor.FileType.FILE_MIRROR_TARGET, FileDescriptor.FileType.FILE_EXISTING_MIRROR_SOURCE },
                new FileDescriptor.FileType[] {});
        if (fileDescriptors.isEmpty()) {
            log.info("No Create Mirror  Steps required");
            return waitFor;
        }
        log.info("Adding Create Mirror steps for create fileshares");
        // Create replication relationships
        waitFor = createElementReplicaSteps(workflow, waitFor, fileDescriptors);

        return waitFor = CREATE_FILE_MIRRORS_STEP;

    }

    /**
     * Expand source file share and target fileshare
     */
    @Override
    public String addStepsForExpandFileSystems(Workflow workflow,
            String waitFor, List<FileDescriptor> fileDescriptors, String taskId)
            throws InternalException {
        // TBD
        return null;
    }

    /**
     * Create Replication Session Step
     * 
     * @param workflow
     * @param waitFor
     * @param fileDescriptors
     * @return
     */
    private String createElementReplicaSteps(final Workflow workflow, String waitFor,
            final List<FileDescriptor> fileDescriptors) {
        log.info("START create element replica steps");

        List<FileDescriptor> sourceDescriptors = FileDescriptor.filterByType(fileDescriptors, FileDescriptor.FileType.FILE_MIRROR_SOURCE,
                FileDescriptor.FileType.FILE_EXISTING_MIRROR_SOURCE);

        Map<URI, FileShare> uriFileShareMap = queryFileShares(fileDescriptors);
        // call to create mirror session
        String newWaitFor = createFileMirrorSession(workflow, waitFor, sourceDescriptors, uriFileShareMap);

        return newWaitFor;
    }

    /**
     * Create Mirror Work Flow Step - creates replication session between source and target
     * 
     * @param workflow
     * @param waitFor
     * @param sourceDescriptors
     * @param uriFileShareMap
     * @return
     */
    protected String createFileMirrorSession(Workflow workflow, String waitFor, List<FileDescriptor> sourceDescriptors,
            Map<URI, FileShare> uriFileShareMap) {

        for (FileDescriptor sourceDescriptor : sourceDescriptors) {
            FileShare source = uriFileShareMap.get(sourceDescriptor.getFsURI());

            for (String targetStr : source.getMirrorfsTargets()) {
                URI targetURI = URI.create(targetStr);

                StorageSystem system = dbClient.queryObject(StorageSystem.class,
                        source.getStorageDevice());

                Workflow.Method createMethod = createMirrorFilePairStep(system.getId(),
                        source.getId(), targetURI, null);
                Workflow.Method rollbackMethod = rollbackMirrorFilePairMethod(system.getId(),
                        source.getId(), targetURI);
                // Ensure CreateElementReplica steps are executed sequentially (CQ613404)
                waitFor = workflow.createStep(CREATE_FILE_MIRRORS_STEP,
                        CREATE_FILE_MIRRORS_STEP_DESC, waitFor, system.getId(),
                        system.getSystemType(), getClass(), createMethod, rollbackMethod, null);
            }
        }

        return waitFor = CREATE_FILE_MIRRORS_STEP;
    }

    private Workflow.Method createMirrorFilePairStep(
            URI systemURI, URI sourceURI, URI targetURI, URI vpoolChangeUri) {
        return new Workflow.Method(CREATE_FILE_MIRROR_PAIR_METH,
                systemURI, sourceURI, targetURI, vpoolChangeUri);
    }

    /**
     * Call to Create Mirror session on Storage Device
     * 
     * @param systemURI
     * @param sourceURI
     * @param targetURI
     * @param vpoolChangeUri
     * @param opId
     * @return
     */
    public boolean createMirrorSession(
            URI systemURI, URI sourceURI, URI targetURI, URI vpoolChangeUri, String opId) {

        log.info("Create Mirror Session between source and Target Pair");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);

            completer = new MirrorFileCreateTaskCompleter(sourceURI, targetURI, vpoolChangeUri, opId);
            getRemoteMirrorDevice(system).doCreateMirrorLink(system, sourceURI, targetURI, completer);
            log.info("Source: {}", sourceURI);
            log.info("Target: {}", targetURI);
            log.info("OpId: {}", opId);
        } catch (Exception e) {
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
            WorkflowStepCompleter.stepFailed(opId, error);
            return false;
        }

        return true;
    }

    // roll back mirror session

    private Workflow.Method rollbackMirrorFilePairMethod(final URI systemURI, final URI sourceURI,
            final URI targetURI) {
        return rollbackMirrorFilePairStep(systemURI, asList(sourceURI), asList(targetURI));
    }

    private Workflow.Method rollbackMirrorFilePairStep(final URI systemURI, final List<URI> sourceURIs,
            final List<URI> targetURIs) {
        return new Workflow.Method(ROLLBACK_MIRROR_LINKS_METHOD, systemURI, sourceURIs, targetURIs);
    }

    /**
     * Roll back Mirror session between source and target
     * 
     * @param systemURI
     * @param sourceURIs
     * @param targetURIs
     * @param opId
     * @return
     */
    public boolean rollbackMirrorFileShareStep(URI systemURI, List<URI> sourceURIs,
            List<URI> targetURIs, String opId) {
        log.info("START rollback Mirror links");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            completer = new FileMirrorRollbackCompleter(sourceURIs, opId);
            getRemoteMirrorDevice(system).doRollbackMirrorLink(system, sourceURIs, targetURIs, completer, opId);
        } catch (Exception e) {
            log.error("Ignoring exception while rolling back Mirror sources: {}", sourceURIs, e);
            // Succeed here, to allow other rollbacks to run
            if (null != completer) {
                completer.ready(dbClient);
            }
            WorkflowStepCompleter.stepSucceded(opId);
            return false;
        }
        return true;
    }

    @Override
    public void performNativeContinuousCopies(URI storage, URI sourceFileShare,
            List<URI> mirrorURIs, String opType, String opId) throws ControllerException {
    }

    /**
     * Convenience method to build a Map of URI's to their respective fileshares based on a List of
     * FileDescriptor.
     * 
     * @param fileShareDescriptors List of fileshare descriptors
     * @return Map of URI to FileShare
     */
    private Map<URI, FileShare> queryFileShares(final List<FileDescriptor> fileShareDescriptors) {
        List<URI> fileShareURIs = FileDescriptor.getFileSystemURIs(fileShareDescriptors);
        List<FileShare> fileShares = dbClient.queryObject(FileShare.class, fileShareURIs);
        Map<URI, FileShare> fileShareMap = new HashMap<URI, FileShare>();
        for (FileShare fileShare : fileShares) {
            if (fileShare != null) {
                fileShareMap.put(fileShare.getId(), fileShare);
            }
        }
        return fileShareMap;
    }

    private StorageSystem getStorageSystem(final URI systemURI) {
        return dbClient.queryObject(StorageSystem.class, systemURI);
    }

    private static final String FAILOVER_MIRROR_FILESHARE_METH = "failoverMirrorFilePair";
    private static final String RESYNC_MIRROR_FILESHARE_METH = "resyncPrepMirrorFilePair";
    private static final String START_MIRROR_FILESHARE_METH = "startPrepMirrorFilePair";
    private static final String CANCEL_MIRROR_FILESHARE_METH = "cancelPrepMirrorFilePair";

    // resyncPrep -step
    public static Workflow.Method
            resyncPrepMirrorPairMeth(URI primarysystemURI, URI targetSystemURI, URI fileshareURI, String policyName) {
        return new Workflow.Method(RESYNC_MIRROR_FILESHARE_METH, primarysystemURI, targetSystemURI, fileshareURI, policyName);
    }

    // start Mirror -step
    public static Workflow.Method
            startMirrorPairMeth(URI storage, URI fsURI, String policyName) {
        return new Workflow.Method(START_MIRROR_FILESHARE_METH, storage, fsURI, policyName);
    }

    // failover Mirror -step
    public static Workflow.Method
            faioverMirrorPairMeth(URI storage, URI fsURI, String policyName) {
        return new Workflow.Method(FAILOVER_MIRROR_FILESHARE_METH, storage, fsURI, policyName);
    }

    // Cancel Mirror Policy -step
    public static Workflow.Method
            cancelMirrorPairMeth(URI storage, URI fsURI, String policyName) {
        return new Workflow.Method(CANCEL_MIRROR_FILESHARE_METH, storage, fsURI, policyName);
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

    /**
     * A rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain. Can be and is
     * used in workflows in other controllers that invoke operations on this
     * block controller. If the block operation happens to fail, this no-op
     * rollback method is invoked. It says the rollback step succeeded,
     * which will then allow other rollback operations to execute for other
     * workflow steps executed by the other controller.
     * 
     * @param stepId The id of the step being rolled back.
     * 
     * @throws WorkflowException
     */
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    public String gerneratePolicyName(StorageSystem system, FileShare fileShareTarget) {
        return fileShareTarget.getLabel();
    }

    /**
     * Common method used to create Controller methods that would be executed by workflow service
     * 
     * @param workflow
     * @param stepGroup
     * @param waitFor - String
     * @param methodName - Name of the method to be executed
     * @param stepId - String unique id of the step
     * @param stepDescription - String description of the step
     * @param storage - URI of the StorageSystem
     * @param args - Parameters of the method that has to be executed by workflow
     * @return waitForStep
     */
    public String createMethod(Workflow workflow, String stepGroup, String waitFor, String methodName, String stepId,
            String stepDescription, URI storage, Object[] args) {
        StorageSystem system = this.dbClient.queryObject(StorageSystem.class, storage);
        Workflow.Method method = new Workflow.Method(methodName, args);
        String waitForStep = workflow.createStep(stepGroup, stepDescription, waitFor, storage, system.getSystemType(), getClass(), method,
                null, stepId);
        return waitForStep;
    }

	@Override
	public String addStepsForReduceFileSystems(Workflow workflow, String waitFor, List<FileDescriptor> fileDescriptors,
			String taskId) throws InternalException {
		return null;
	}

}
