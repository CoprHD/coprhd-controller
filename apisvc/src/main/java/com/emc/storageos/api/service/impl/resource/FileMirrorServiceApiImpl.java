/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;

import java.net.URI;
import java.util.*;

import com.emc.storageos.api.service.impl.placement.FileRecommendation;
import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.filereplicationcontroller.FileReplicationController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.NativeContinuousCopyCreate;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.SRDFRecommendation;
import com.emc.storageos.volumecontroller.impl.file.FileCreateWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * Block Service subtask (parts of larger operations) Replication implementation.
 */
public class FileMirrorServiceApiImpl extends AbstractFileServiceApiImpl<FileStorageScheduler> {

    private static final Logger _log = LoggerFactory.getLogger(FileMirrorServiceApiImpl.class);

    public FileMirrorServiceApiImpl() {
        super(null);
    }

    private DefaultFileServiceApiImpl _defaultFileServiceApiImpl;

    public DefaultFileServiceApiImpl getDefaultFileServiceApiImpl() {
        return _defaultFileServiceApiImpl;
    }

    public void setDefaultFileServiceApiImpl(
            DefaultFileServiceApiImpl defaultFileServiceApiImpl) {
        this._defaultFileServiceApiImpl = defaultFileServiceApiImpl;
    }

    /**
     * it take mirror recommendation and then creates source and mirror fileshare
     */
    @Override
    public TaskList createFileSystems(FileSystemParam param, Project project, VirtualArray varray,
            VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags, List<Recommendation> recommendations,
            TaskList taskList, String taskId, VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        // TBD
        return _defaultFileServiceApiImpl.createFileSystems(param, project, varray, vpool, tenantOrg, flags,
                recommendations, taskList, taskId, vpoolCapabilities);
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType,
            boolean forceDelete, String task) throws InternalException {
        _log.info("Request to delete {} FileShare(s) with Mirror Protection", fileSystemURIs.size());
        super.deleteFileSystems(systemURI, fileSystemURIs, deletionType, forceDelete, task);

    }

    @Override
    protected List<FileDescriptor> getDescriptorsOfFileShareDeleted(
            URI systemURI, List<URI> fileShareURIs, String deletionType,
            boolean forceDelete) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TaskList startNativeContinuousCopies(StorageSystem storageSystem,
            FileShare sourceFileShare, VirtualPool sourceVirtualPool,
            VirtualPoolCapabilityValuesWrapper capabilities,
            NativeContinuousCopyCreate param, String taskId)
            throws ControllerException {
        // TODO Auto-generated method stub
        TaskList taskList = new TaskList();
        List<FileShare> fileShares = new ArrayList<FileShare>();

        populateFsRecommendations(capabilities, sourceVirtualPool, sourceFileShare, taskId, taskList, null,  fileShares);

        List<URI> mirrorList = new ArrayList<URI>(fileShares.size());
        for (FileShare fileShare : fileShares) {
            Operation op = _dbClient.createTaskOpStatus(MirrorFileShare.class, fileShare.getId(),
                    taskId, ResourceOperationTypeEnum.ATTACH_BLOCK_MIRROR);
            fileShare.getOpStatus().put(taskId, op);
            TaskResourceRep volumeTask = toTask(fileShare, taskId, op);
            taskList.getTaskList().add(volumeTask);
            mirrorList.add(fileShare.getId());
        }

        StorageSystem system = _dbClient.queryObject(StorageSystem.class,
                sourceFileShare.getStorageDevice());
        FileReplicationController controller = getController(FileReplicationController.class,
                system.getSystemType());

        try {
            controller.performNativeContinuousCopies(system.getId(), sourceFileShare.getId(),
                    mirrorList, FileService.ProtectionOp.START.getRestOp(), taskId);
        } catch (ControllerException ce) {
            String errorMsg = format("Failed to start continuous copies on volume %s: %s",
                    sourceFileShare.getId(), ce.getMessage());

            _log.error(errorMsg, ce);
            for (TaskResourceRep taskResourceRep : taskList.getTaskList()) {
                taskResourceRep.setState(Operation.Status.error.name());
                taskResourceRep.setMessage(errorMsg);
                Operation statusUpdate = new Operation(Operation.Status.error.name(), errorMsg);
                _dbClient.updateTaskOpStatus(Volume.class, taskResourceRep.getResource().getId(),
                                        taskId, statusUpdate);
            }
            throw ce;
        }

        return taskList;

    }

    @Override
    public TaskList stopNativeContinuousCopies(StorageSystem storageSystem,
            FileShare sourceFileShare, List<URI> mirrorFSUris, String taskId)
            throws ControllerException {
        // TODO Auto-generated method stub
        TaskList taskList = new TaskList();
        // TBD call the FileReplicationDevice controller
        return taskList;
    }

    //Populate the Recommendations for the given sourceVolume

    private void populateFsRecommendations(VirtualPoolCapabilityValuesWrapper capabilities,
                                               VirtualPool sourceFsVPool, FileShare sourceFileShare,
                                               String taskId, TaskList taskList,
                                               String fileShareLabel, List<FileShare> preparedFileShares) {
        List<Recommendation> currentRecommendation = new ArrayList<Recommendation>();
        List<FileRecommendation> fileRecommendations = null;
        //vpool
        VirtualPool mirrorVPool = sourceFsVPool;
        //varray
        VirtualArray vArray = _dbClient.queryObject(VirtualArray.class, sourceFileShare.getVirtualArray());
        //project
        Project project = _dbClient.queryObject(Project.class, sourceFileShare.getOriginalProject());
        //attribute map
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        Set<String> storageSystemSet = new HashSet<String>();
        storageSystemSet.add(sourceFileShare.getStorageDevice().toString());
        attributeMap.put(AttributeMatcher.Attributes.storage_system.name(), storageSystemSet);

        Set<String> virtualArraySet = new HashSet<String>();
        virtualArraySet.add(vArray.getId().toString());
        attributeMap.put(AttributeMatcher.Attributes.varrays.name(), virtualArraySet);
        //call to get recommendations
        fileRecommendations = _scheduler.getRecommendationsForMirrors(vArray, mirrorVPool,
                                                                capabilities, project, attributeMap);
        for(FileRecommendation fileRecommendation: fileRecommendations) {
            fileRecommendation.setFileType(FileRecommendation.FileType.FILE_SYSTEM_LOCAL_MIRROR);
        }
        List<FileShare> fileList = null;

        // Prepare the FileShares
        fileList = _scheduler.prepareFileSystems(null, taskId, taskList, project,vArray,
                sourceFsVPool, currentRecommendation, capabilities, false);

        preparedFileShares.addAll(fileList);

    }



}
