/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.filereplicationcontroller;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_STRING_TO_URI;
import static com.google.common.collect.Collections2.transform;
import static java.util.Arrays.asList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationInterface;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorCancelTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorDetachTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorRollbackCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileCreateTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileFailoverTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFilePauseTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileResumeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileStartTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileStopTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.RemoteFileMirrorOperation;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
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
    private static final String DELETE_FILE_MIRRORS_STEP = "DELETE_FILE_MIRRORS_STEP";
    private static final String DETACH_FILE_MIRRORS_STEP = "DETACH_FILE_MIRRORS_STEP";

    private static final String CREATE_FILE_MIRROR_PAIR_METH = "createMirrorSession";
    private static final String DETACH_FILE_MIRROR_PAIR_METH = "detachMirrorFilePairStep";
    private static final String CANCEL_FILE_MIRROR_PAIR_METH = "cancelMirrorFilePairStep";
    private static final String ROLLBACK_MIRROR_LINKS_METHOD = "rollbackMirrorFileShareStep";

    private static final String CREATE_FILE_MIRRORS_STEP_DESC = "Create MirrorFileShare Link";
    private static final String DETACH_FILE_MIRRORS_STEP_DESC = "Detach MirrorFileShare Link";
    private static final String CANCEL_FILE_MIRRORS_STEP_DESC = "Cancel MirrorFileShare Link";

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
                new FileDescriptor.Type[] { FileDescriptor.Type.FILE_MIRROR_SOURCE,
                        FileDescriptor.Type.FILE_MIRROR_TARGET }, new FileDescriptor.Type[] {});
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
     * Delete mirror session or link between source filesystem and target fileshare
     */
    @Override
    public String addStepsForDeleteFileSystems(Workflow workflow,
            String waitFor, List<FileDescriptor> filesystems, String taskId)
            throws InternalException {
        List<FileDescriptor> sourceDescriptors = FileDescriptor.filterByType(
                filesystems, FileDescriptor.Type.FILE_MIRROR_SOURCE);
        if (sourceDescriptors.isEmpty()) {
            return waitFor;
        }
        waitFor = deleteElementReplicaSteps(workflow, waitFor, sourceDescriptors);

        return waitFor;
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

        List<FileDescriptor> sourceDescriptors =
                FileDescriptor.filterByType(fileDescriptors, FileDescriptor.Type.FILE_MIRROR_SOURCE);

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
            StringSet mirrorTargets = source.getMirrorfsTargets();

            for (String targetStr : mirrorTargets) {
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
            getRemoteMirrorDevice(system).doRollbackMirrorLink(system, sourceURIs, targetURIs, completer);
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

    /**
     * Delete Replication session
     * 
     * @param workflow
     * @param waitFor
     * @param fileDescriptors
     * @return
     */
    private String deleteElementReplicaSteps(final Workflow workflow, String waitFor,
            final List<FileDescriptor> fileDescriptors) {
        log.info("START create element replica steps");
        StorageSystem system = null;

        Map<URI, FileShare> uriFileShareMap = queryFileShares(fileDescriptors);

        for (FileShare source : uriFileShareMap.values()) {
            StringSet mirrorTargets = source.getMirrorfsTargets();
            system = dbClient.queryObject(StorageSystem.class, source.getStorageDevice());
            for (String mirrorTarget : mirrorTargets) {

                URI targetURI = URI.create(mirrorTarget);
                FileShare target = dbClient.queryObject(FileShare.class, targetURI);
                if (null == target) {
                    log.warn("Target FileShare {} not available for Mirror source FileShare {}", source.getId(), targetURI);
                    // We need to proceed with the operation, as it could be because of a left over from last operation.
                    return waitFor;
                } else {

                    Workflow.Method detachMethod = detachMirrorPairMethod(system.getId(), source.getId(), targetURI);
                    String detachStep = workflow.createStep(DELETE_FILE_MIRRORS_STEP,
                            DETACH_FILE_MIRRORS_STEP_DESC, waitFor, system.getId(),
                            system.getSystemType(), getClass(), detachMethod, null, null);
                    waitFor = detachStep;

                }

            }
        }

        return waitFor = DELETE_FILE_MIRRORS_STEP;
    }

    private Workflow.Method cancelMirrorLinkMethod(URI systemURI, URI sourceURI, URI targetURI) {
        return new Workflow.Method(CANCEL_FILE_MIRROR_PAIR_METH, systemURI, sourceURI, targetURI);
    }

    /**
     * Cancel Mirror session
     * 
     * @param systemURI
     * @param sourceURI
     * @param targetURI
     * @param opId
     * @return
     */
    public boolean cancelMirrorFilePairStep(URI systemURI, URI sourceURI, URI targetURI, String opId) {
        log.info("START Suspend Mirror link");
        TaskCompleter completer = null;

        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            FileShare target = dbClient.queryObject(FileShare.class, targetURI);
            List<URI> combined = Arrays.asList(sourceURI, targetURI);
            completer = new FileMirrorCancelTaskCompleter(combined, opId);
            getRemoteMirrorDevice(system).doCancelMirrorLink(system, target, completer);
            WorkflowStepCompleter.stepExecuting(opId);
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

    private Method detachMirrorPairMethod(URI systemURI, URI sourceURI, URI targetURI) {
        return new Method(DETACH_FILE_MIRROR_PAIR_METH, systemURI, sourceURI, targetURI);
    }

    /**
     * Detach Mirror session between between source and target
     * 
     * @param systemURI
     * @param sourceURI
     * @param targetURI
     * @param opId
     * @return
     */
    public boolean detachMirrorFilePairStep(URI systemURI, URI sourceURI, URI targetURI, String opId) {
        log.info("START Detach Pair ={}", sourceURI.toString());
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            completer = new FileMirrorDetachTaskCompleter(sourceURI, opId);
            getRemoteMirrorDevice(system).doDetachMirrorLink(system, sourceURI, targetURI, completer);
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

    @Override
    public void performNativeContinuousCopies(URI storage, URI sourceFileShare,
            List<URI> mirrorURIs, String opType, String opId) throws ControllerException {
    }

    @Override
    public void performRemoteContinuousCopies(URI storage, URI copyId,
            String opType, String opId) throws ControllerException {

        StorageSystem system = dbClient.queryObject(StorageSystem.class, storage);

        FileShare fileShare = dbClient.queryObject(FileShare.class, copyId);
        List<String> targetfileUris = new ArrayList<String>();
        List<URI> combined = new ArrayList<URI>();
        if (PersonalityTypes.SOURCE.toString().equalsIgnoreCase(fileShare.getPersonality())) {
            targetfileUris.addAll(fileShare.getMirrorfsTargets());

            combined.add(fileShare.getId());
            combined.addAll(transform(fileShare.getMirrorfsTargets(), FCTN_STRING_TO_URI));
        }
        TaskCompleter completer = null;
        try {
            if (opType.equalsIgnoreCase("failover")) {

                for (String target : targetfileUris) {
                    FileShare targetFileShare = dbClient.queryObject(FileShare.class, URI.create(target));
                    completer = new MirrorFileFailoverTaskCompleter(FileShare.class, fileShare.getId(), opId);
                    StorageSystem systemTarget = dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());
                    getRemoteMirrorDevice(systemTarget).doFailoverLink(systemTarget, targetFileShare, completer);
                }

            } else if (opType.equalsIgnoreCase("pause")) {
                completer = new MirrorFilePauseTaskCompleter(FileShare.class, combined, opId);
                for (String target : targetfileUris) {
                    FileShare targetFileShare = dbClient.queryObject(FileShare.class, URI.create(target));
                    getRemoteMirrorDevice(system).doSuspendLink(system, targetFileShare, completer);
                }

            } else if (opType.equalsIgnoreCase("failback")) {

            } else if (opType.equalsIgnoreCase("resume")) {
                completer = new MirrorFileResumeTaskCompleter(FileShare.class, combined, opId);
                for (String target : targetfileUris) {
                    FileShare targetFileShare = dbClient.queryObject(FileShare.class, URI.create(target));
                    getRemoteMirrorDevice(system).doResumeLink(system, targetFileShare, completer);
                }

            } else if (opType.equalsIgnoreCase("start")) {
                for (String target : targetfileUris) {

                    FileShare targetFileShare = dbClient.queryObject(FileShare.class, URI.create(target));
                    completer = new MirrorFileStartTaskCompleter(FileShare.class, fileShare.getId(), opId);

                    getRemoteMirrorDevice(system).doStartMirrorLink(system, targetFileShare, completer);
                }
            } else if (opType.equalsIgnoreCase("sync")) {

            } else if (opType.equalsIgnoreCase("stop")) {
                for (String target : targetfileUris) {

                    FileShare targetFileShare = dbClient.queryObject(FileShare.class, URI.create(target));
                    completer = new MirrorFileStopTaskCompleter(fileShare.getId(), targetFileShare.getId(), opId);
                    getRemoteMirrorDevice(system).doStopMirrorLink(system, targetFileShare, completer);
                }
            }

        } catch (Exception e) {
            log.error("Failed operation {}", opType, e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            if (null != completer) {
                completer.error(dbClient, error);
            }
        }
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

    private static final String FAILBACK_MIRROR_FILESHARE_WF_NAME = "FAILBACK_MIRROR_FILESHARE_WORKFLOW";

    private static final String FAILOVER_MIRROR_FILESHARE_STEP = "failoverMirrorFilePairStep";
    private static final String RESYNC_MIRROR_FILESHARE_STEP = "reSyncPrepMirrorFilePairStep";
    private static final String START_MIRROR_FILESHARE_STEP = "reSyncPrepMirrorFilePairStep";

    private static final String FAILOVER_MIRROR_FILESHARE_METH = "failoverMirrorFilePair";
    private static final String RESYNC_MIRROR_FILESHARE_METH = "resyncPrepMirrorFilePair";
    private static final String START_MIRROR_FILESHARE_METH = "startPrepMirrorFilePair";

    private static final String FAILOVER_FILE_MIRRORS_STEP_DESC = "failover MirrorFileShare Link";
    private static final String RESYNC_MIRROR_FILESHARE_STEP_DESC = "resync MirrorFileShare Link";
    private static final String START_MIRROR_FILESHARE_STEP_DES = "start MirrorFileShare Link";

    void doFailBackMirrorSession(StorageSystem primarysystem, FileShare sourceFileShare,
            List<String> targetfileUris, String taskId) {
        // Generate the Workflow.
        Workflow workflow = workflowService.getNewWorkflow(this,
                FAILBACK_MIRROR_FILESHARE_WF_NAME, false, taskId);
        String waitFor = null;

        for (String target : targetfileUris) {
            // target share
            FileShare targetFileShare = dbClient.queryObject(FileShare.class, URI.create(target));
            // target storage system
            StorageSystem secondarysystem = dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());
            String policyName = targetFileShare.getLabel();

            Workflow.Method resyncMethod = resyncPrepMirrorPairMeth(primarysystem.getId(), secondarysystem.getId(),
                    sourceFileShare.getId(), policyName);
            String descFailover = String.format("Creating resyncprep between source- %s and target %s", primarysystem.getLabel(),
                    secondarysystem.getLabel());
            // step -1
            waitFor = workflow.createStep(
                    RESYNC_MIRROR_FILESHARE_STEP,
                    descFailover,
                    waitFor, primarysystem.getId(), primarysystem.getSystemType(), getClass(),
                    resyncMethod,
                    rollbackMethodNullMethod(), null);

            // step -2
            waitFor = workflow.createStep(
                    START_MIRROR_FILESHARE_STEP,
                    START_MIRROR_FILESHARE_STEP_DES,
                    waitFor, primarysystem.getId(), primarysystem.getSystemType(), getClass(),
                    startMirrorPairMeth(secondarysystem.getId(), sourceFileShare.getId(), policyName),
                    rollbackMethodNullMethod(), null);

            // step -3
            waitFor = workflow.createStep(
                    FAILOVER_MIRROR_FILESHARE_STEP,
                    FAILOVER_FILE_MIRRORS_STEP_DESC,
                    waitFor, primarysystem.getId(), primarysystem.getSystemType(), getClass(),
                    faioverMirrorPairMeth(primarysystem.getId(), sourceFileShare.getId(), policyName),
                    rollbackMethodNullMethod(), null);

            // step -4

            resyncMethod = resyncPrepMirrorPairMeth(secondarysystem.getId(), primarysystem.getId(),
                    sourceFileShare.getId(), policyName);
            waitFor = workflow.createStep(
                    RESYNC_MIRROR_FILESHARE_STEP,
                    descFailover,
                    waitFor, secondarysystem.getId(), secondarysystem.getSystemType(), getClass(),
                    resyncMethod,
                    rollbackMethodNullMethod(), null);

        }
    }

    // resyncPrep
    public static Workflow.Method
            resyncPrepMirrorPairMeth(URI primarysystemURI, URI targetSystemURI, URI fileshareURI, String policyName) {
        return new Workflow.Method(RESYNC_MIRROR_FILESHARE_METH, primarysystemURI, targetSystemURI, fileshareURI, policyName);
    }

    public void resyncPrepMirrorFilePair(URI primarysystemURI, URI targetSystemURI, URI fileshareURI, String policyName) {

    }

    // start Mirror
    public static Workflow.Method
            startMirrorPairMeth(URI storage, URI fsURI, String policyName) {
        return new Workflow.Method(START_MIRROR_FILESHARE_METH, storage, fsURI, policyName);
    }

    public void startPrepMirrorFilePair(URI storage, URI fileshareURI, String policyName) {

    }

    // stop mirror

    // start Mirror
    public static Workflow.Method
            faioverMirrorPairMeth(URI storage, URI fsURI, String policyName) {
        return new Workflow.Method(START_MIRROR_FILESHARE_METH, storage, fsURI, policyName);
    }

    public void failoverMirrorFilePair(URI storage, URI fileshareURI, String policyName) {

    }

    /**
     * Creates a rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain.
     *
     * @return A workflow method
     */
    Workflow.Method rollbackMethodNullMethod() {
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
     * See the VPlexDeviceController restoreVolume method which creates a
     * workflow step that invokes the BlockDeviceController restoreVolume
     * method. The rollback method for this step is this no-op. If the
     * BlockDeviceController restoreVolume step fails, this rollback
     * method is invoked, which simply says the rollback for the step
     * was successful. This in turn allows the other steps in the workflow
     * rollback.
     *
     * @param stepId The id of the step being rolled back.
     *
     * @throws WorkflowException
     */
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

}
