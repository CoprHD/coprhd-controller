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

import com.emc.storageos.api.service.impl.placement.FileMirrorSchedular;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.fileorchestrationcontroller.FileDescriptor;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Strings;

public class FileRemoteMirrorServiceApiImpl extends AbstractFileServiceApiImpl<FileMirrorSchedular> {

    private static final Logger _log = LoggerFactory.getLogger(FileRemoteMirrorServiceApiImpl.class);

    private FileMirrorServiceApiImpl _fileMirrorServiceApiImpl;

    public FileMirrorServiceApiImpl getFileMirrorServiceApiImpl() {
        return _fileMirrorServiceApiImpl;
    }

    public void setFileMirrorServiceApiImpl(FileMirrorServiceApiImpl _fileMirrorServiceApiImpl) {
        this._fileMirrorServiceApiImpl = _fileMirrorServiceApiImpl;
    }

    public FileRemoteMirrorServiceApiImpl() {
        super(null);
    }

    public FileRemoteMirrorServiceApiImpl(String protectionType) {
        super(protectionType);
        // TODO Auto-generated constructor stub
    }

    @Override
    public TaskList createFileSystems(FileSystemParam param, Project project, VirtualArray varray,
            VirtualPool vpool, TenantOrg tenantOrg, DataObject.Flag[] flags, List<Recommendation> recommendations,
            TaskList taskList, String taskId, VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        return getFileMirrorServiceApiImpl().createFileSystems(param, project, varray, vpool, tenantOrg, flags,
                recommendations, taskList, taskId, vpoolCapabilities);
    }

    @Override
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType,
            boolean forceDelete, boolean deleteOnlyMirrors, String task)
            throws InternalException {
        super.deleteFileSystems(systemURI, fileSystemURIs, deletionType, forceDelete, deleteOnlyMirrors, task);
    }

    @Override
    public TaskResourceRep createTargetsForExistingSource(FileShare fs, Project project,
            VirtualPool vpool, VirtualArray varray, TaskList taskList, String task, List<Recommendation> recommendations,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities) throws InternalException {
        return getFileMirrorServiceApiImpl().createTargetsForExistingSource(fs, project, vpool, varray, taskList,
                task, recommendations, vpoolCapabilities);

    }

    @Override
    protected List<FileDescriptor> getDescriptorsOfFileShareDeleted(
            URI systemURI, List<URI> fileShareURIs, String deletionType,
            boolean forceDelete, boolean deleteOnlyMirrors) {
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
                    throw APIException.badRequests.cannotDeleteMirrorFileShareTargetWithActiveSource(fileURI,
                            fileShare.getParentFileShare().getURI());
                }
            } else {
                descriptorType = FileDescriptor.Type.FILE_MIRROR_SOURCE;
            }

            FileDescriptor fileDescriptor = new FileDescriptor(descriptorType,
                    fileShare.getStorageDevice(), fileShare.getId(),
                    fileShare.getPool(), deletionType, forceDelete, deleteOnlyMirrors);
            fileDescriptors.add(fileDescriptor);
        }
        return fileDescriptors;
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

}
