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
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileCreateTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileTaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.RemoteFileMirrorOperation;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

import static java.util.Arrays.asList;
public class FileReplicationDeviceController implements FileOrchestrationInterface, FileReplicationController{
	private static final Logger log = LoggerFactory.getLogger(FileReplicationDeviceController.class);
	
    private WorkflowService workflowService;
    private DbClient dbClient;
    private Map<String, FileStorageDevice> devices;
	
	private static final String CREATE_FILE_MIRRORS_STEP = "CREATE_FILE_MIRRORS_STEP";
	private static final String DELETE_FILE_MIRRORS_STEP = "DELETE_FILE_MIRRORS_STEP";
	private static final String RESYNC_FILE_MIRRORS_STEP = "RESYNC_FILE_MIRRORS_STEP";
	
	private static final String CREATE_FILE_MIRROR_PAIR_METH = "createMirrorFilePairStep";
	private static final String DETACH_FILE_MIRROR_PAIR_METH = "deleteMirrorFilePairStep";
	private static final String ROLLBACK_MIRROR_LINKS_METHOD = "rollbackMirrorLinksStep";
	
	private static final String CREATE_FILE_MIRRORS_STEP_DESC = "Create MirrorFileShare Link";
	private static final String DETACH_FILE_MIRRORS_STEP_DESC = "Detach MirrorFileShare Link";
	
	
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
	
	@Override
	public String addStepsForCreateFileSystems(Workflow workflow,
			String waitFor, List<FileDescriptor> filesystems, String taskId)
			throws InternalException {
		// TODO Auto-generated method stub
		List<FileDescriptor> fileMirrorDescriptors = FileDescriptor.filterByType(filesystems,
                new FileDescriptor.Type[] { FileDescriptor.Type.FILE_RP_SOURCE,
                        FileDescriptor.Type.FILE_RP_TARGET,
                        }, new FileDescriptor.Type[] {});
        if (fileMirrorDescriptors .isEmpty()) {
            log.info("No Mirror Steps required");
            return waitFor;
        }
        log.info("Adding Mirror  steps for create MirrorFileShare");
        // Create Mirror relationships
        waitFor = createElementReplicaSteps(workflow, waitFor, fileMirrorDescriptors);
        return waitFor;

	}

	@Override
	public String addStepsForDeleteFileSystems(Workflow workflow,
			String waitFor, List<FileDescriptor> filesystems, String taskId)
			throws InternalException {
		List<FileDescriptor> sourceDescriptors = FileDescriptor.filterByType(filesystems,
                FileDescriptor.Type.FILE_RP_SOURCE);
        if (sourceDescriptors.isEmpty()) {
            return waitFor;
        }
		// TODO Auto-generated method stub
		deleteMirrorFileSteps(workflow, waitFor, sourceDescriptors);
		return waitFor;
	}

	@Override
	public String addStepsForExpandFileSystems(Workflow workflow,
			String waitFor, List<FileDescriptor> fileDescriptors, String taskId)
			throws InternalException {
		// TODO Auto-generated method stub
		return null;
	}
	
	private String createElementReplicaSteps(final Workflow workflow, String waitFor,
            final List<FileDescriptor> fileDescriptors) {
		List<FileDescriptor> sourceDescriptors = FileDescriptor.filterByType(fileDescriptors,
                FileDescriptor.Type.FILE_RP_SOURCE);
        List<FileDescriptor> targetDescriptors = FileDescriptor.filterByType(fileDescriptors,
                FileDescriptor.Type.FILE_RP_TARGET);
        Map<URI, FileShare> uriFsMap = queryVolumes(fileDescriptors);
        
        
        
//        Workflow.Method createMethod = createReplicationFilePairMethod(system.getId(),
//                source.getId(), targetURI, vpoolChangeUri);
//        Workflow.Method rollbackMethod = rollbackMirrorLinkMethod(system.getId(),
//                source.getId(), targetURI, false);
//        
//        // Ensure CreateElementReplica steps are executed sequentially (CQ613404)
//        waitFor = workflow.createStep(CREATE_FILE_MIRROR_PAIR_METH,
//        		CREATE_FILE_MIRRORS_STEP_DESC, waitFor, system.getId(),
//                system.getSystemType(), getClass(), createMethod, rollbackMethod, null);


		
        //prepare the replication pair method
		
		return waitFor;
	}
	
	private Workflow.Method createReplicationFilePairMethod(final URI systemURI,
            final URI sourceURI, final URI targetURI, final URI vpoolChangeUri) {
			return new Workflow.Method(CREATE_FILE_MIRROR_PAIR_METH, 
					systemURI, sourceURI, targetURI, vpoolChangeUri);
	}
	
	private String deleteMirrorFileSteps(final Workflow workflow, String waitFor,
            final List<FileDescriptor> sourceDescriptors) {
		Map<URI, FileShare> sourcesVolumeMap = queryVolumes(sourceDescriptors);
//		Workflow.Method detachMethod = detachMirrorFileSharePairMethod(system.getId(), source.getId(), targetURI);
//        String detachStep = workflow.createStep(DELETE_FILE_MIRRORS_STEP,
//        		DETACH_FILE_MIRROR_PAIR_METH, waitFor, system.getId(),
//                system.getSystemType(), getClass(), detachMethod, null, null);
//        waitFor = detachStep;

		return null;
	}
		
	public boolean createMirrorFileSharePairStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final URI vpoolChangeUri,
            final String opId) {
		log.info("START Add file replication pair");
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
	
	private Workflow.Method rollbackMirrorLinksMethod(final URI systemURI, final List<URI> sourceURIs,
            final List<URI> targetURIs) {
        return new Workflow.Method(ROLLBACK_MIRROR_LINKS_METHOD, systemURI, sourceURIs, targetURIs);
    }
	
	
	public boolean rollbackMirrorLinksStep(URI systemURI, List<URI> sourceURIs,
            List<URI> targetURIs, boolean isGroupRollback, String opId) {
		return false;
	}
	
	
	/**
     * Convenience method to build a Map of URI's to their respective fileshares based on a List of
     * FileDescriptor.
     *
     * @param fileDescriptors List of file descriptors
     * @return Map of URI to FileShare
     */
    private Map<URI, FileShare> queryVolumes(final List<FileDescriptor> fileDescriptors) {
        List<URI> fileURIs = FileDescriptor.getFileSystemURIs(fileDescriptors);
        List<FileShare> fileShares = dbClient.queryObject(FileShare.class, fileURIs);
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
    
    
    private String deleteMirrorFileShareSteps(final Workflow workflow, String waitFor,
            final List<FileDescriptor> sourceDescriptors) {
    	
    	return waitFor;
    	
    }
    private Method detachMirrorFileSharePairMethod(URI systemURI, URI sourceURI, URI targetURI) {
        return new Workflow.Method(DETACH_FILE_MIRROR_PAIR_METH, systemURI, sourceURI, targetURI, false);
    }
    
    public boolean deleteMirrorFilePairStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final String opId) {
    	log.info("START Detach Pair ");
        TaskCompleter completer = null;
        try {
            WorkflowStepCompleter.stepExecuting(opId);
            StorageSystem system = getStorageSystem(systemURI);

            completer = new MirrorFileTaskCompleter(sourceURI, targetURI, opId);
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
    
    
}
