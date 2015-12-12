/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.filereplicationcontroller;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationInterface;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

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
	
	private static final String CREATE_FILE_MIRRORS_STEP_DESC = "Create MirrorFileShare Link";
	private static final String DETACH_FILE_MIRRORS_STEP_DESC = "Detach MirrorFileShare Link";
	
	
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
		return null;
	}

	@Override
	public String addStepsForDeleteFileSystems(Workflow workflow,
			String waitFor, List<FileDescriptor> filesystems, String taskId)
			throws InternalException {
		// TODO Auto-generated method stub
		return null;
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
		
		return waitFor;
	}
	
	private Workflow.Method createReplicationFilePairMethod(final URI systemURI,
            final URI sourceURI, final URI targetURI, final URI vpoolChangeUri) {
			return new Workflow.Method(CREATE_FILE_MIRROR_PAIR_METH, systemURI, sourceURI, targetURI, vpoolChangeUri);
	}
	
	public boolean createMirrorFileSharePairStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final URI vpoolChangeUri,
            final String opId) {
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
    
    public boolean detachMirrorFileSharePairStep(final URI systemURI, final URI sourceURI,
            final URI targetURI, final String opId) {
    	return false;
    
    }

	
}
