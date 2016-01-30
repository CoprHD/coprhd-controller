/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * File Service subtask (parts of larger operations) default implementation.
 */
public class DefaultFileServiceApiImpl extends AbstractFileServiceApiImpl<FileStorageScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(DefaultFileServiceApiImpl.class);

    public DefaultFileServiceApiImpl() {
        super(null);
    }

    @Override
    public TaskList createFileSystems(FileSystemParam param, Project project,
            VirtualArray varray, VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags,
            List<Recommendation> recommendations, TaskList taskList, String task,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {

        List<FileShare> fileList = null;
        List<FileShare> fileShares = new ArrayList<FileShare>();

        // Prepare the FileShares
        fileList = getFileScheduler().prepareFileSystems(param, task, taskList, project,
                varray, vpool, recommendations, vpoolCapabilities, false);

        fileShares.addAll(fileList);
        // prepare the file descriptors
        String suggestedNativeFsId = param.getFsId() == null ? "" : param.getFsId();
        final List<FileDescriptor> fileDescriptors = prepareFileDescriptors(fileShares, vpoolCapabilities, suggestedNativeFsId);
        final FileOrchestrationController controller = getController(FileOrchestrationController.class,
                FileOrchestrationController.FILE_ORCHESTRATION_DEVICE);
        try {
            // Execute the fileshare creations requests
            controller.createFileSystems(fileDescriptors, task);
        } catch (InternalException e) {
            _log.error("Controller error when creating filesystems", e);
            failFileShareCreateRequest(task, taskList, fileShares, e.getMessage());
            throw e;
        } catch (Exception e) {
            _log.error("Controller error when creating filesystems", e);
            failFileShareCreateRequest(task, taskList, fileShares, e.getMessage());
            throw e;
        }
        return taskList;

    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType, boolean forceDelete, String task)
            throws InternalException {
        super.deleteFileSystems(systemURI, fileSystemURIs, deletionType, forceDelete, task);
    }

    @Override
    public TaskResourceRep changeFileSystemVirtualPool(FileShare fs, Project project,
            VirtualPool vpool, VirtualArray varray, TaskList taskList, String task, List<Recommendation> recommendations,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        try {
            super.changeFileSystemVirtualPool(fs, project, vpool, varray, taskList, task,
                    recommendations, vpoolCapabilities);
        } catch (Exception e) {
            _log.error("Controller error when changing filesystem vpool", e);
            throw e;
        }
        return taskList.getTaskList().get(0);
    }

    @Override
    public TaskResourceRep createTargetsForExistingSource(FileShare fs, Project project,
            VirtualPool vpool, VirtualArray varray, TaskList taskList, String task, List<Recommendation> recommendations,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        try {
            super.createTargetsForExistingSource(fs, project, vpool, varray, taskList, task,
                    recommendations, vpoolCapabilities);
        } catch (Exception e) {
            _log.error("Controller error when create mirror filesystems", e);
            throw e;
        }
        return taskList.getTaskList().get(0);
    }

    /**
     * prepare the file descriptors
     * 
     * @param filesystems
     * @param cosCapabilities
     * @param suggestedId
     * @return
     */
    private List<FileDescriptor> prepareFileDescriptors(List<FileShare> filesystems,
            VirtualPoolCapabilityValuesWrapper cosCapabilities, String suggestedId) {

        // Build up a list of FileDescriptors based on the fileshares
        final List<FileDescriptor> fileDescriptors = new ArrayList<FileDescriptor>();
        for (FileShare filesystem : filesystems) {
            FileDescriptor desc = new FileDescriptor(FileDescriptor.Type.FILE_DATA,
                    filesystem.getStorageDevice(), filesystem.getId(),
                    filesystem.getPool(), filesystem.getCapacity(), cosCapabilities, null, suggestedId);

            fileDescriptors.add(desc);
        }
        return fileDescriptors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<FileDescriptor> getDescriptorsOfFileShareDeleted(URI systemURI,
            List<URI> fileShareURIs, String deletionType, boolean forceDelete) {
        List<FileDescriptor> fileDescriptors = new ArrayList<FileDescriptor>();
        for (URI fileShareURI : fileShareURIs) {
            FileShare filesystem = _dbClient.queryObject(FileShare.class, fileShareURI);

            FileDescriptor fileDescriptor = new FileDescriptor(FileDescriptor.Type.FILE_DATA,
                    filesystem.getStorageDevice(), filesystem.getId(),
                    filesystem.getPool(), deletionType, forceDelete);
            fileDescriptors.add(fileDescriptor);
        }
        return fileDescriptors;
    }

    private void failFileShareCreateRequest(String task, TaskList taskList, List<FileShare> preparedFileShares, String errorMsg) {
        String errorMessage = String.format("Controller error: %s", errorMsg);
        for (TaskResourceRep fileShareTask : taskList.getTaskList()) {
            fileShareTask.setState(Operation.Status.error.name());
            fileShareTask.setMessage(errorMessage);
            Operation statusUpdate = new Operation(Operation.Status.error.name(), errorMessage);
            _dbClient.updateTaskOpStatus(FileShare.class, fileShareTask.getResource()
                    .getId(), task, statusUpdate);
        }
        for (FileShare fileShare : preparedFileShares) {
            fileShare.setInactive(true);
            _dbClient.updateObject(fileShare);
        }
    }

}
