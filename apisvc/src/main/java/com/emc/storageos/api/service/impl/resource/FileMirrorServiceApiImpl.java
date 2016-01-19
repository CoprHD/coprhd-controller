/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.FileMirrorRecommendation;
import com.emc.storageos.api.service.impl.placement.FileMirrorRecommendation.Target;
import com.emc.storageos.api.service.impl.placement.FileMirrorSchedular;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.FileAccessState;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.NativeContinuousCopyCreate;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Strings;

/**
 * Block Service subtask (parts of larger operations) Replication implementation.
 */
public class FileMirrorServiceApiImpl extends AbstractFileServiceApiImpl<FileMirrorSchedular> {

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

        List<FileShare> fileList = null;
        List<FileShare> fileShares = new ArrayList<FileShare>();

        // Prepare the FileShares
        fileList = prepareFileSystems(param, taskId, taskList, project, tenantOrg, flags,
                varray, vpool, recommendations, vpoolCapabilities, false);
        fileShares.addAll(fileList);

        // prepare the file descriptors
        String suggestedNativeFsId = param.getFsId() == null ? "" : param.getFsId();
        final List<FileDescriptor> fileDescriptors = prepareFileDescriptors(fileShares, vpoolCapabilities, suggestedNativeFsId);
        final FileOrchestrationController controller = getController(FileOrchestrationController.class,
                FileOrchestrationController.FILE_ORCHESTRATION_DEVICE);
        try {
            // Execute the fileshare creations requests
            controller.createFileSystems(fileDescriptors, taskId);
        } catch (InternalException e) {
            _log.error("Controller error when creating mirror filesystems", e);
            failFileShareCreateRequest(taskId, taskList, fileShares, e.getMessage());
            throw e;
        } catch (Exception e) {
            _log.error("Controller error when creating mirror filesystems", e);
            failFileShareCreateRequest(taskId, taskList, fileShares, e.getMessage());
            throw e;
        }
        return taskList;
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
            FileDescriptor.Type fileType = FileDescriptor.Type.FILE_MIRROR_SOURCE;

            if (filesystem.getPersonality().equals(FileShare.PersonalityTypes.TARGET.toString())) {
                fileType = FileDescriptor.Type.FILE_MIRROR_TARGET;
            }
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities = new VirtualPoolCapabilityValuesWrapper(cosCapabilities);
            FileDescriptor desc = new FileDescriptor(fileType,
                    filesystem.getStorageDevice(), filesystem.getId(),
                    filesystem.getPool(), filesystem.getCapacity(), vpoolCapabilities, null, suggestedId);

            fileDescriptors.add(desc);
        }
        return fileDescriptors;
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType,
            boolean forceDelete, String task) throws InternalException {
        _log.info("Request to delete {} FileShare(s) with Mirror Protection", fileSystemURIs.size());
        super.deleteFileSystems(systemURI, fileSystemURIs, deletionType, forceDelete, task);

    }

    private boolean isParentInactiveForTarget(FileShare fileShare) {
        NamedURI parent = fileShare.getParentFileShare();
        if (NullColumnValueGetter.isNullNamedURI(parent)) {
            String msg = String.format("FileShare %s has no Replication parent", fileShare.getId());
            throw new IllegalStateException(msg);
        }
        FileShare parentFileShare = _dbClient.queryObject(FileShare.class, parent.getURI());
        return parentFileShare == null || parentFileShare.getInactive();
    }

    private FileShare.PersonalityTypes getPersonality(FileShare fileShare) {
        if (Strings.isNullOrEmpty(fileShare.getPersonality())) {
            String msg = String.format("FileShare %s has no personality", fileShare.getId());
            throw new IllegalStateException(msg);
        }
        return FileShare.PersonalityTypes.valueOf(fileShare.getPersonality());
    }

    @Override
    protected List<FileDescriptor> getDescriptorsOfFileShareDeleted(
            URI systemURI, List<URI> fileShareURIs, String deletionType, boolean forceDelete) {
        List<FileDescriptor> fileDescriptors = new ArrayList<FileDescriptor>();
        for (URI fileURI : fileShareURIs) {
            FileShare fileShare = _dbClient.queryObject(FileShare.class, fileURI);
            FileDescriptor.Type descriptorType;
            if (fileShare.getPersonality() == null || fileShare.getPersonality().contains("null")) {
                descriptorType = FileDescriptor.Type.FILE_DATA;
            } else if (FileShare.PersonalityTypes.TARGET == getPersonality(fileShare)) {
                if (isParentInactiveForTarget(fileShare)) {
                    descriptorType = FileDescriptor.Type.FILE_DATA;
                } else {
                    _log.warn("Attempted to delete an Mirror target that had an active Mirror source");
                    throw APIException.badRequests.cannotDeleteSRDFTargetWithActiveSource(fileURI,
                            fileShare.getParentFileShare().getURI());
                }
            } else {
                descriptorType = FileDescriptor.Type.FILE_MIRROR_SOURCE;
            }

            FileDescriptor fileDescriptor = new FileDescriptor(descriptorType,
                    fileShare.getStorageDevice(), fileShare.getId(),
                    fileShare.getPool(), deletionType, forceDelete);
            fileDescriptors.add(fileDescriptor);
        }
        return fileDescriptors;
    }

    @Override
    public TaskList startNativeContinuousCopies(StorageSystem storageSystem,
            FileShare sourceFileShare, VirtualPool sourceVirtualPool,
            VirtualPoolCapabilityValuesWrapper capabilities,
            NativeContinuousCopyCreate param, String taskId)
            throws ControllerException {
        // TODO Auto-generated method stub
        TaskList taskList = new TaskList();
        // TBD call the FileReplicationDevice controller
        return taskList;
    }

    @Override
    public TaskList stopNativeContinuousCopies(StorageSystem storageSystem,
            Volume sourceFileShare, List<URI> mirrorFSUris, String taskId)
            throws ControllerException {
        // TODO Auto-generated method stub
        TaskList taskList = new TaskList();
        // TBD call the FileReplicationDevice controller
        return taskList;
    }

    /**
     * 
     * @param param
     * @param task
     * @param taskList
     * @param project
     * @param varray
     * @param vpool
     * @param recommendations
     * @param cosCapabilities
     * @param createInactive
     * @return
     */
    public List<FileShare> prepareFileSystems(FileSystemParam param, String task, TaskList taskList,
            Project project, TenantOrg tenantOrg, DataObject.Flag[] flags, VirtualArray varray, VirtualPool vpool,
            List<Recommendation> recommendations, VirtualPoolCapabilityValuesWrapper cosCapabilities, Boolean createInactive) {

        List<FileShare> preparedFileSystems = new ArrayList<>();
        Iterator<Recommendation> recommendationsIter = recommendations.iterator();
        while (recommendationsIter.hasNext()) {
            FileMirrorRecommendation recommendation = (FileMirrorRecommendation) recommendationsIter.next();
            // If id is already set in recommendation, do not prepare the fileSystem (fileSystem already exists)
            if (recommendation.getId() != null) {
                continue;
            }

            // get the source file share
            FileShare sourceFileShare = getPrecreatedFile(taskList, param.getLabel());

            // Set the recommendation
            _log.info(String.format("createFileSystem --- FileShare: %1$s, StoragePool: %2$s, StorageSystem: %3$s",
                    sourceFileShare.getId(), recommendation.getSourceStoragePool(), recommendation.getSourceStorageSystem()));
            ValidateFileSystem(recommendation, sourceFileShare);

            // set the source mirror recommendations
            setFileMirrorRecommendation(recommendation, vpool, varray, false, false, sourceFileShare);

            FileShare targetFileShare = null;
            StringBuilder fileLabelBuilder = null;
            VirtualPool targetVpool = vpool;

            if (vpool.getFileReplicationType().equals(FileReplicationType.LOCAL.name())) {
                fileLabelBuilder = new StringBuilder(sourceFileShare.getLabel()).append("-target-" + varray.getLabel());

                targetFileShare = prepareEmptyFileSystem(fileLabelBuilder.toString(), sourceFileShare.getCapacity(),
                        project, recommendation, tenantOrg, varray, vpool, targetVpool, flags, task);
                // Set target file recommendations to target file system!!!
                setFileMirrorRecommendation(recommendation, vpool, varray, true, false, targetFileShare);

                // Update the source and target relationship!!
                setMirrorFileShareAttributes(sourceFileShare, targetFileShare);
                preparedFileSystems.add(sourceFileShare);
                preparedFileSystems.add(targetFileShare);

            } else {

                Map<URI, VpoolRemoteCopyProtectionSettings> settingMap = VirtualPool.getFileRemoteProtectionSettings(vpool, _dbClient);
                VpoolRemoteCopyProtectionSettings protectionSettings = null;
                List<VirtualArray> virtualArrayTargets = FileMirrorSchedular.getTargetVirtualArraysForVirtualPool(project, vpool,
                        _dbClient, getPermissionsHelper());

                // Source file system!!
                preparedFileSystems.add(sourceFileShare);

                for (VirtualArray targetVArray : virtualArrayTargets) {
                    protectionSettings = settingMap.get(targetVArray.getId());
                    if (protectionSettings.getVirtualPool() != null) {
                        targetVpool = _dbClient.queryObject(VirtualPool.class, protectionSettings.getVirtualPool());
                    }

                    fileLabelBuilder = new StringBuilder(sourceFileShare.toString()).append("-target-" + targetVArray.getLabel());
                    targetFileShare = prepareEmptyFileSystem(fileLabelBuilder.toString(), sourceFileShare.getCapacity(),
                            project, recommendation, tenantOrg, varray, vpool, targetVpool, flags, task);

                    // Set target file recommendations to target file system!!!
                    setFileMirrorRecommendation(recommendation, targetVpool, targetVArray, true, false, targetFileShare);

                    // Update the source and target relationship!!
                    setMirrorFileShareAttributes(sourceFileShare, targetFileShare);
                    preparedFileSystems.add(targetFileShare);
                }
            }
        }
        return preparedFileSystems;
    }

    /**
     * 
     * @param sourceFileShare
     * @param targetFileShare
     */
    void setMirrorFileShareAttributes(FileShare sourceFileShare, FileShare targetFileShare) {

        if (sourceFileShare != null) {

            if (sourceFileShare.getMirrorfsTargets() == null) {
                sourceFileShare.setMirrorfsTargets(new StringSet());
            }
            sourceFileShare.getMirrorfsTargets().add(targetFileShare.getId().toString());

            targetFileShare.setParentFileShare(new NamedURI(sourceFileShare.getId(), sourceFileShare.getLabel()));
            _dbClient.updateObject(sourceFileShare);
            _dbClient.updateObject(targetFileShare);

        }
    }

    /**
     * 
     * @param placement
     * @param fileShare
     */
    void ValidateFileSystem(FileMirrorRecommendation placement, FileShare fileShare) {
        // Now check whether the label used in the storage system or not
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, placement.getSourceStorageSystem());
        List<FileShare> fileShareList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileShare.class,
                PrefixConstraint.Factory.getFullMatchConstraint(FileShare.class, "label", fileShare.getLabel()));
        if (fileShareList != null && fileShareList.isEmpty()) {
            for (FileShare fs : fileShareList) {
                if (fs.getStorageDevice() != null) {
                    if (fs.getStorageDevice().equals(system.getId())) {
                        _log.info("Duplicate label found {} on Storage System {}", fileShare.getLabel(), system.getId());
                        throw APIException.badRequests.duplicateLabel(fileShare.getLabel());
                    }
                }
            }
        }
    }

    /**
     * Convenience method to return a file from a task list with a pre-labeled fileshare.
     * 
     * @param taskList
     * @param label
     * @return
     */
    public FileShare getPrecreatedFile(TaskList taskList, String label) {

        for (TaskResourceRep task : taskList.getTaskList()) {
            FileShare fileShare = _dbClient.queryObject(FileShare.class, task.getResource().getId());
            if (fileShare.getLabel().equalsIgnoreCase(label)) {
                return fileShare;
            }
        }
        return null;
    }

    /**
     * create fileSystem
     * 
     * @param newFileLabel
     * @param fileshareSize
     * @param project
     * @param fileMirrorRecommendation
     * @param tenantOrg
     * @param varray
     * @param sourceVpool
     * @param targetVpool
     * @param flags
     * @param taskId
     * @return
     */
    private FileShare prepareEmptyFileSystem(String newFileLabel, Long fileshareSize, Project project,
            FileMirrorRecommendation fileMirrorRecommendation, TenantOrg tenantOrg,
            VirtualArray varray, VirtualPool sourceVpool, VirtualPool targetVpool, DataObject.Flag[] flags, String taskId) {

        _log.debug("prepareEmptyFileSystem start...");

        FileShare fs = new FileShare();
        fs.setId(URIUtil.createId(FileShare.class));

        validateFileShareLabel(newFileLabel, project);

        fs.setLabel(newFileLabel);

        // No need to generate any name -- Since the requirement is to use the customizing label we should use the same.
        // Stripping out the special characters like ; /-+!@#$%^&())";:[]{}\ | but allow underscore character _
        String convertedName = newFileLabel.replaceAll("[^\\dA-Za-z\\_]", "");
        _log.info("Original name {} and converted name {}", newFileLabel, convertedName);
        fs.setName(convertedName);
        fs.setCapacity(fileshareSize);

        // set tenant
        fs.setTenant(new NamedURI(tenantOrg.getId(), newFileLabel));

        // set vpool
        VirtualPool vpool = sourceVpool;
        if (targetVpool != null) {
            vpool = targetVpool;
        }
        fs.setVirtualPool(vpool.getId());

        // set varray
        fs.setVirtualArray(varray.getId());

        // set project
        if (project != null) {
            fs.setProject(new NamedURI(project.getId(), fs.getLabel()));
        }

        // set prov type
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(vpool.getSupportedProvisioningType())) {
            fs.setThinlyProvisioned(Boolean.TRUE);
        }

        // set internal flags
        if (flags != null) {
            fs.addInternalFlags(flags);
        }

        fs.setOpStatus(new OpStatusMap());
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_FILE_SYSTEM);
        fs.getOpStatus().createTaskStatus(taskId, op);

        _dbClient.createObject(fs);
        return fs;
    }

    /**
     * prepare the source and target filesystem using Recommandations
     * 
     * @param placement
     * @param vpoolSource
     * @param vpoolTarget
     * @param isTargetFS
     * @param createInactive
     * @param fileShare
     */
    public void setFileMirrorRecommendation(FileMirrorRecommendation placement,
            final VirtualPool virtualPool, final VirtualArray virtualArray, final boolean isTargetFS, final Boolean createInactive,
            FileShare fileShare) {
        StoragePool pool = null;

        if (isTargetFS == false) {
            if (null != placement.getSourceStoragePool()) {
                pool = _dbClient.queryObject(StoragePool.class, placement.getSourceStoragePool());
                fileShare.setPersonality(FileShare.PersonalityTypes.SOURCE.toString());
                fileShare.setAccessState(FileAccessState.READWRITE.name());
            }
            // set the storage ports
            if (placement.getStoragePorts() != null && !placement.getStoragePorts().isEmpty()) {
                fileShare.setStoragePort(placement.getStoragePorts().get(0));
            }

            // set vnas server
            if (placement.getvNAS() != null) {
                fileShare.setVirtualNAS(placement.getvNAS());
            }
        } else {

            Map<URI, FileMirrorRecommendation.Target> targetMap = placement.getVirtualArrayTargetMap();
            if (targetMap != null && !targetMap.isEmpty()) {
                Target target = targetMap.get(virtualArray.getId());
                if (target != null) {
                    pool = _dbClient.queryObject(StoragePool.class, target.getTargetStoragePool());
                    fileShare.setPersonality(FileShare.PersonalityTypes.TARGET.toString());
                    fileShare.setAccessState(FileAccessState.READABLE.name());

                    // set the target ports
                    if (target.getTargetStoragePortUris() != null && !target.getTargetStoragePortUris().isEmpty()) {
                        fileShare.setStoragePort(target.getTargetStoragePortUris().get(0));
                    }

                    // set the target vNAS
                    if (target.getTargetvNASURI() != null) {
                        fileShare.setVirtualNAS(target.getTargetvNASURI());
                    }
                }

            }
        }

        // set vpool
        if (null != pool) {
            fileShare.setStorageDevice(pool.getStorageDevice());
            fileShare.setPool(pool.getId());

            fileShare.setProtocol(new StringSet());
            fileShare.getProtocol().addAll(VirtualPoolUtil.getMatchingProtocols(virtualPool.getProtocols(), virtualPool.getProtocols()));
        }

        _dbClient.updateObject(fileShare);
        // finally set file share id in recommendation
        placement.setId(fileShare.getId());
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

    /**
     * validate the given fileshare label is not a duplicate within given project. If so, throw exception
     * 
     * @param label - label to validate
     * @param project - project where label is being validate.
     */
    protected void validateFileShareLabel(String label, Project project) {
        List<FileShare> fileShareList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileShare.class,
                ContainmentPrefixConstraint.Factory.getFullMatchConstraint(Volume.class, "project", project.getId(), label));
        if (!fileShareList.isEmpty()) {
            throw APIException.badRequests.duplicateLabel(label);
        }
    }

}
