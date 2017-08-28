/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.policy.FilePolicyUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class FileOrchestrationControllerImpl implements FileOrchestrationController {

    private Dispatcher _dispatcher;
    private FileOrchestrationController _controller;
    private DbClient _dbClient;

    @Override
    public void createFileSystems(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {

        execOrchestration("createFileSystems", fileDescriptors, taskId);
    }

    @Override
    public void deleteFileSystems(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {
        execOrchestration("deleteFileSystems", fileDescriptors, taskId);
    }

    @Override
    public void expandFileSystem(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {
        execOrchestration("expandFileSystem", fileDescriptors, taskId);
    }
    
    @Override
    public void reduceFileSystem(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {
        execOrchestration("reduceFileSystem", fileDescriptors, taskId);
    }

    @Override
    public void createTargetsForExistingSource(String sourceFs,
            List<FileDescriptor> fileDescriptors, String taskId) throws ControllerException {
        execOrchestration("createTargetsForExistingSource", sourceFs, fileDescriptors, taskId);
    }

    @Override
    public void createCIFSShare(URI storageSystem, URI fileSystem, FileSMBShare smbShare, String taskId) throws ControllerException {
        execOrchestration("createCIFSShare", storageSystem, fileSystem, smbShare, taskId);
    }

    @Override
    public void createNFSExport(URI storage, URI fsURI, List<FileShareExport> exports, String opId) throws ControllerException {
        execOrchestration("createNFSExport", storage, fsURI, exports, opId);
    }

    @Override
    public void updateExportRules(URI storage, URI fsURI, FileExportUpdateParams param, boolean unmountExport, String opId)
            throws ControllerException {
        execOrchestration("updateExportRules", storage, fsURI, param, unmountExport, opId);
    }

    @Override
    public void updateShareACLs(URI storage, URI fsURI, String shareName, CifsShareACLUpdateParams param, String opId)
            throws ControllerException {
        execOrchestration("updateShareACLs", storage, fsURI, shareName, param, opId);
    }

    @Override
    public void snapshotFS(URI storage, URI snapshot, URI fsURI, String opId) throws ControllerException {
        execOrchestration("snapshotFS", storage, snapshot, fsURI, opId);
    }

    @Override
    public void deleteShare(URI storage, URI uri, FileSMBShare fileSMBShare, String task) throws ControllerException {
        execOrchestration("deleteShare", storage, uri, fileSMBShare, task);
    }

    @Override
    public void deleteExportRules(URI storage, URI uri, boolean allDirs, String subDirs, boolean unmountExport, String taskId)
            throws ControllerException {
        execOrchestration("deleteExportRules", storage, uri, allDirs, subDirs, unmountExport, taskId);
    }

    @Override
    public void failoverFileSystem(URI fsURI, StoragePort nfsPort, StoragePort cifsPort, boolean replicateConfiguration, String taskId)
            throws ControllerException {
        execOrchestration("failoverFileSystem", fsURI, nfsPort, cifsPort, replicateConfiguration, taskId);
    }

    @Override
    public void failbackFileSystem(URI fsURI, StoragePort nfsPort, StoragePort cifsPort, boolean replicateConfiguration, String taskId)
            throws ControllerException {
        execOrchestration("failbackFileSystem", fsURI, nfsPort, cifsPort, replicateConfiguration, taskId);
    }

    @Override
    public void restoreFS(URI storage, URI fs, URI snapshot, String opId) throws ControllerException {
        execOrchestration("restoreFS", storage, fs, snapshot, opId);
    }

    @Override
    public void deleteSnapshot(URI storage, URI pool, URI uri, boolean forceDelete, String deleteType, String opId)
            throws ControllerException {
        execOrchestration("deleteSnapshot", storage, pool, uri, forceDelete, deleteType, opId);
    }

    @Override
    public void deleteShareACLs(URI storage, URI uri, String shareName, String taskId) throws ControllerException {
        execOrchestration("deleteShareACLs", storage, uri, shareName, taskId);
    }

    @Override
    public void unassignFilePolicy(URI policy, Set<URI> unassignFrom, String taskId) throws InternalException {
        execOrchestration("unassignFilePolicy", policy, unassignFrom, taskId);

    }

    @Override
    public void assignFileSnapshotPolicyToVirtualPools(Map<URI, List<URI>> vpoolToStorageSystemMap, URI filePolicyToAssign, String taskId)
            throws InternalException {
        execOrchestration("assignFileSnapshotPolicyToVirtualPools", vpoolToStorageSystemMap, filePolicyToAssign, taskId);
    }

    @Override
    public void assignFileSnapshotPolicyToProjects(Map<URI, List<URI>> vpoolToStorageSystemMap, List<URI> projectURIs,
            URI filePolicyToAssign, String taskId) {
        execOrchestration("assignFileSnapshotPolicyToProjects", vpoolToStorageSystemMap, projectURIs, filePolicyToAssign, taskId);

    }

    @Override
    public void updateFileProtectionPolicy(URI policy, FilePolicyUpdateParam param, String taskId) {
        execOrchestration("updateFileProtectionPolicy", policy, param, taskId);
    }

    @Override
    public void assignFileReplicationPolicyToVirtualPools(List<FileStorageSystemAssociation> associations,
            List<URI> vpoolURIs, URI filePolicyToAssign, String taskId) {
        execOrchestration("assignFileReplicationPolicyToVirtualPools", associations, vpoolURIs, filePolicyToAssign, taskId);

    }

    @Override
    public void assignFileReplicationPolicyToProjects(List<FileStorageSystemAssociation> associations, URI vpoolURI, List<URI> projectURIs,
            URI filePolicyToAssign, String taskId) {
        execOrchestration("assignFileReplicationPolicyToProjects", associations, vpoolURI, projectURIs, filePolicyToAssign, taskId);

    }

    @Override
    public void assignFilePolicyToFileSystem(FilePolicy filePolicy, List<FileDescriptor> fileDescriptors, String taskId)
            throws ControllerException {
        execOrchestration("assignFilePolicyToFileSystem", filePolicy, fileDescriptors, taskId);

    }

    // getter and setter methods
    public FileOrchestrationController getController() {
        return _controller;
    }

    public void setController(FileOrchestrationController controller) {
        this._controller = controller;
    }

    public Dispatcher getDispatcher() {
        return _dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this._dispatcher = dispatcher;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    private void execOrchestration(String methodName, Object... args) throws ControllerException {
        _dispatcher.queue(NullColumnValueGetter.getNullURI(), FILE_ORCHESTRATION_DEVICE,
                getController(), methodName, args);
    }
}
