/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.filereplicationcontroller;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.DbClient;

import static java.util.Arrays.asList;

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
    private static final String SUSPEND_FILE_MIRRORS_STEP_DESC = "suspend MirrorFileShare Link";

    /**
     * calls to remote mirror operations on devices
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
     * create mirror session or link between source fileshare and target fileshare
     */

    @Override
    public String addStepsForCreateFileSystems(Workflow workflow,
            String waitFor, List<FileDescriptor> filesystems, String taskId)
            throws InternalException {
        // TBD
        return null;

    }

    /**
     * delete mirror session or link between source filesystem and target fileshare
     */
    @Override
    public String addStepsForDeleteFileSystems(Workflow workflow,
            String waitFor, List<FileDescriptor> filesystems, String taskId)
            throws InternalException {
        // TBD
        return null;
    }

    /**
     * expand source file share and target fileshare
     */
    @Override
    public String addStepsForExpandFileSystems(Workflow workflow,
            String waitFor, List<FileDescriptor> fileDescriptors, String taskId)
            throws InternalException {
        // TBD
        return null;
    }

    @Override
    public void performNativeContinuousCopies(URI storage, URI sourceFileShare,
            List<URI> mirrorURIs, String opType, String opId)
            throws ControllerException {
        // TODO Auto-generated method stub
        //call local mirror operations
    }

    @Override
    public void performRemoteContinuousCopies(URI storage, URI copyId,
            String opType, String opId) throws ControllerException {
        // TODO Auto-generated method stub
        //TBD remote mirror operations
    }
    
    
    private String createElementReplicaSteps(final Workflow workflow, String waitFor,
            final List<FileDescriptor> fileDescriptors) {
        log.info("START create element replica steps");

        List<FileDescriptor> sourceDescriptors =
                FileDescriptor.filterByType(fileDescriptors, FileDescriptor.Type.FILE_MIRROR_SOURCE);

        Map<URI, FileShare> uriFileShareMap = queryFileShares(fileDescriptors);
        //call to create mirror session
        createFileMirrorSession(workflow, waitFor, sourceDescriptors, uriFileShareMap);
        
        return null;
    }
    
    protected void createFileMirrorSession(Workflow workflow, String waitFor, List<FileDescriptor> sourceDescriptors,
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
        
        
    }
    
    private Workflow.Method createMirrorFilePairStep(final URI systemURI,
            final URI sourceURI, final URI targetURI, final URI vpoolChangeUri) {
        return new Workflow.Method(CREATE_FILE_MIRROR_PAIR_METH, systemURI, sourceURI, targetURI, vpoolChangeUri);
    }
    
    public boolean createMirrorSession(final URI systemURI, final URI sourceURI,
            final URI targetURI, final URI vpoolChangeUri, final String opId) {
        
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
    
    //roll back mirror session
 // Convenience method for singular usage of #rollbackSRDFLinksMethod
    private Workflow.Method rollbackMirrorFilePairMethod(final URI systemURI, final URI sourceURI,
                                                   final URI targetURI) {
        return rollbackMirrorFilePairStep(systemURI, asList(sourceURI), asList(targetURI));
    }

    private Workflow.Method rollbackMirrorFilePairStep(final URI systemURI, final List<URI> sourceURIs,
                                                    final List<URI> targetURIs) {
        return new Workflow.Method(ROLLBACK_MIRROR_LINKS_METHOD, systemURI, sourceURIs, targetURIs);
    }


    public boolean rollbackMirrorFileShareStep(URI systemURI, List<URI> sourceURIs,
                                         List<URI> targetURIs, String opId) {
        log.info("START rollback multiple SRDF links");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);
            completer = new FileMirrorRollbackCompleter(sourceURIs, opId);
            getRemoteMirrorDevice(system).doRollbackMirrorLink(system, sourceURIs, targetURIs, completer);
        } catch (Exception e) {
            log.error("Ignoring exception while rolling back SRDF sources: {}", sourceURIs, e);
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

}
