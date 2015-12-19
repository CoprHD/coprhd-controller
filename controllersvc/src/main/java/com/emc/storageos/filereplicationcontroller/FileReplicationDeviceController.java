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


import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationInterface;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.impl.file.RemoteFileMirrorOperation;

import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.DbClient;

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
	
	/**
	 * calls to remote mirror operations on devices
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
		//TBD
		return null;

	}
	
	/**
	 * delete mirror session or link between source filesystem and target fileshare
	 */
	@Override
	public String addStepsForDeleteFileSystems(Workflow workflow,
			String waitFor, List<FileDescriptor> filesystems, String taskId)
			throws InternalException {
		//TBD
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

}
