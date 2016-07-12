/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.filereplicationcontroller.FileReplicationDeviceController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.file.CreateMirrorFileSystemsCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileCreateWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileDeleteWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileSnapshotWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeFSSnapshotTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;

public class FileOrchestrationDeviceController implements FileOrchestrationController, Controller {
    private static final Logger s_logger = LoggerFactory.getLogger(FileOrchestrationDeviceController.class);

    private static DbClient s_dbClient;
    private WorkflowService _workflowService;
    private static FileDeviceController _fileDeviceController;
    private static FileReplicationDeviceController _fileReplicationDeviceController;
    private ControllerLockingService _locker;

    static final String CREATE_FILESYSTEMS_WF_NAME = "CREATE_FILESYSTEMS_WORKFLOW";
    static final String DELETE_FILESYSTEMS_WF_NAME = "DELETE_FILESYSTEMS_WORKFLOW";
    static final String EXPAND_FILESYSTEMS_WF_NAME = "EXPAND_FILESYSTEMS_WORKFLOW";
    static final String CHANGE_FILESYSTEMS_VPOOL_WF_NAME = "CHANGE_FILESYSTEMS_VPOOL_WORKFLOW";
    static final String CREATE_MIRROR_FILESYSTEMS_WF_NAME = "CREATE_MIRROR_FILESYSTEMS_WORKFLOW";
    static final String CREATE_FILESYSTEM_CIFS_SHARE_WF_NAME = "CREATE_FILESYSTEM_CIFS_SHARE_WORKFLOW";
    static final String CREATE_FILESYSTEM_NFS_EXPORT_WF_NAME = "CREATE_FILESYSTEM_NFS_EXPORT_WORKFLOW";
    static final String UPDATE_FILESYSTEM_EXPORT_RULES_WF_NAME = "UPDATE_FILESYSTEM_EXPORT_RULES_WORKFLOW";
    static final String UPDATE_FILESYSTEM_CIFS_SHARE_ACLS_WF_NAME = "UPDATE_FILESYSTEM_CIFS_SHARE_ACLS_WORKFLOW";
    static final String CREATE_FILESYSTEM_SNAPSHOT_WF_NAME = "CREATE_FILESYSTEM_SNAPSHOT_WORKFLOW";
    static final String DELETE_FILESYSTEM_CIFS_SHARE_WF_NAME = "DELETE_FILESYSTEM_CIFS_SHARE_WORKFLOW";
    static final String DELETE_FILESYSTEM_EXPORT_RULES_WF_NAME = "DELETE_FILESYSTEM_EXPORT_RULES_WORKFLOW";
    private static final String CREATE_FILESYSTEM_EXPORT_METHOD = "export";
    private static final String CREATE_FILESYSTEM_SHARE_METHOD = "share";
    private static final String UPDATE_FILESYSTEM_SHARE_ACLS_METHOD = "updateShareACLs";
    private static final String UPDATE_FILESYSTEM_EXPORT_RULES_METHOD = "updateExportRules";
    private static final String CREATE_FILESYSTEM_SNAPSHOT_METHOD = "snapshotFS";
    private static final String DELETE_FILESYSTEM_SHARE_METHOD = "deleteShare";
    private static final String DELETE_FILESYSTEM_EXPORT_RULES = "deleteExportRules";

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController#createFileSystems(java.util.List, java.lang.String)
     */

    /**
     * Creates one or more filesystem
     * (FileShare, FileMirroring). This method is responsible for creating
     * a Workflow and invoking the FileOrchestrationInterface.addStepsForCreateFileSystems
     * 
     * @param fileDescriptors
     * @param taskId
     * @throws ControllerException
     */
    @Override
    public void createFileSystems(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {

        // Generate the Workflow.
        Workflow workflow = null;
        List<URI> fsUris = FileDescriptor.getFileSystemURIs(fileDescriptors);

        FileCreateWorkflowCompleter completer = new FileCreateWorkflowCompleter(fsUris, taskId, fileDescriptors);
        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this,
                    CREATE_FILESYSTEMS_WF_NAME, false, taskId);
            String waitFor = null;    // the wait for key returned by previous call

            s_logger.info("Generating steps for create FileSystem");
            // First, call the FileDeviceController to add its methods.
            waitFor = _fileDeviceController.addStepsForCreateFileSystems(workflow, waitFor,
                    fileDescriptors, taskId);
            // second, call create replication link or pair
            waitFor = _fileReplicationDeviceController.addStepsForCreateFileSystems(workflow, waitFor,
                    fileDescriptors, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Create filesystems successful for: " + fsUris.toString();
            Object[] callbackArgs = new Object[] { fsUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);

        } catch (Exception ex) {
            s_logger.error("Could not create filesystems: " + fsUris, ex);
            releaseWorkflowLocks(workflow);
            String opName = ResourceOperationTypeEnum.CREATE_FILE_SYSTEM.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController#changeFileSystemVirtualPool(java.util.List,
     * java.lang.String)
     */

    /**
     * Create target filesystems for existing file systems!!
     * (FileShare, FileMirroring). This method is responsible for creating
     * a Workflow and invoking the FileOrchestrationInterface.addStepsForCreateFileSystems
     * 
     * @param filesystems
     * @param taskId
     * @throws ControllerException
     */
    @Override
    public void createTargetsForExistingSource(String fs, List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {

        // Generate the Workflow.
        Workflow workflow = null;
        List<URI> fsUris = FileDescriptor.getFileSystemURIs(fileDescriptors);

        CreateMirrorFileSystemsCompleter completer = new CreateMirrorFileSystemsCompleter(fsUris, taskId, fileDescriptors);
        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this,
                    CREATE_MIRROR_FILESYSTEMS_WF_NAME, false, taskId);
            String waitFor = null;    // the wait for key returned by previous call

            s_logger.info("Generating steps for creating mirror filesystems...");
            // First, call the FileDeviceController to add its methods.
            // To create target file systems!!
            waitFor = _fileDeviceController.addStepsForCreateFileSystems(workflow, waitFor,
                    fileDescriptors, taskId);
            // second, call create replication link or pair
            waitFor = _fileReplicationDeviceController.addStepsForCreateFileSystems(workflow, waitFor,
                    fileDescriptors, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Change filesystems vpool successful for: " + fs;
            Object[] callbackArgs = new Object[] { fsUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);

        } catch (Exception ex) {
            s_logger.error("Could not change the filesystem vpool: " + fs, ex);
            releaseWorkflowLocks(workflow);
            String opName = ResourceOperationTypeEnum.CHANGE_FILE_SYSTEM_VPOOL.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController#deleteFileSystems(java.util.List, java.lang.String)
     */
    /**
     * Deletes one or more filesystem.
     * 
     * @param fileDescriptors
     * @param taskId
     * @throws ControllerException
     */
    @Override
    public void deleteFileSystems(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {
        String waitFor = null;    // the wait for key returned by previous call
        List<URI> fileShareUris = FileDescriptor.getFileSystemURIs(fileDescriptors);
        FileDeleteWorkflowCompleter completer = new FileDeleteWorkflowCompleter(fileShareUris, taskId);
        Workflow workflow = null;

        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this,
                    DELETE_FILESYSTEMS_WF_NAME, false, taskId);

            // Call the FileReplicationDeviceController to add its delete methods if there are Mirror FileShares.
            waitFor = _fileReplicationDeviceController.addStepsForDeleteFileSystems(workflow,
                    waitFor, fileDescriptors, taskId);

            // Next, call the FileDeviceController to add its delete methods.
            waitFor = _fileDeviceController.addStepsForDeleteFileSystems(workflow, waitFor, fileDescriptors, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Delete FileShares successful for: " + fileShareUris.toString();
            Object[] callbackArgs = new Object[] { fileShareUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);

        } catch (Exception ex) {
            s_logger.error("Could not delete FileShares: " + fileShareUris, ex);
            releaseWorkflowLocks(workflow);
            String opName = ResourceOperationTypeEnum.DELETE_FILE_SYSTEM.getName();
            ServiceError serviceError = DeviceControllerException.errors.deleteFileSharesFailed(
                    fileShareUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController#expandFileSystem(java.net.URI, long, java.lang.String)
     */
    /**
     * expand one or more filesystem
     * 
     * @param fileDescriptors
     * @param taskId
     * @throws ControllerException
     */
    @Override
    public void expandFileSystem(List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {
        String waitFor = null;    // the wait for key returned by previous call
        List<URI> fileShareUris = FileDescriptor.getFileSystemURIs(fileDescriptors);
        FileWorkflowCompleter completer = new FileWorkflowCompleter(fileShareUris, taskId);
        Workflow workflow = null;
        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this,
                    EXPAND_FILESYSTEMS_WF_NAME, false, taskId);
            // Next, call the FileDeviceController to add its delete methods.
            waitFor = _fileDeviceController.addStepsForExpandFileSystems(workflow, waitFor, fileDescriptors, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Expand FileShares successful for: " + fileShareUris.toString();
            Object[] callbackArgs = new Object[] { fileShareUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not Expand FileShares: " + fileShareUris, ex);
            releaseWorkflowLocks(workflow);
            String opName = ResourceOperationTypeEnum.EXPORT_FILE_SYSTEM.getName();
            ServiceError serviceError = DeviceControllerException.errors.expandFileShareFailed(fileShareUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @SuppressWarnings("serial")
    public static class WorkflowCallback implements Workflow.WorkflowCallbackHandler, Serializable {
        @Override
        public void workflowComplete(Workflow workflow, Object[] args)
                throws WorkflowException {
        }
    }

    private void releaseWorkflowLocks(Workflow workflow) {
        if (workflow == null) {
            return;
        }
        s_logger.info("Releasing all workflow locks with owner: {}", workflow.getWorkflowURI());
        _workflowService.releaseAllWorkflowLocks(workflow);
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public DbClient getDbClient() {
        return s_dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        FileOrchestrationDeviceController.s_dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
        this._locker = locker;
    }

    public FileDeviceController getFileDeviceController() {
        return _fileDeviceController;
    }

    public void setFileDeviceController(FileDeviceController fileDeviceController) {
        FileOrchestrationDeviceController._fileDeviceController = fileDeviceController;
    }

    public FileReplicationDeviceController getFileReplicationFileDeviceController() {
        return _fileReplicationDeviceController;
    }

    public void setFileReplicationDeviceController(FileReplicationDeviceController fileReplicationDeviceController) {
        FileOrchestrationDeviceController._fileReplicationDeviceController = fileReplicationDeviceController;
    }

    @Override
    public void createCIFSShare(URI storageSystem, URI uri, FileSMBShare smbShare, String taskId) throws ControllerException {
        FileObject fileObj = null;
        String stepDescription = null;
        String successMessage = null;
        String opName = null;
        TaskCompleter completer = null;
        if (URIUtil.isType(uri, FileShare.class)) {
            completer = new FileWorkflowCompleter(uri, taskId);
            fileObj = s_dbClient.queryObject(FileShare.class, uri);
            stepDescription = "Creating CIFS Share : " + smbShare.getName() + " for FileSystem : " + fileObj.getLabel();
            successMessage = "CIFS Share creation for FileSystem :" + fileObj.getLabel() + " finished successfully";
            opName = ResourceOperationTypeEnum.CREATE_FILE_SYSTEM_SHARE.getName();
        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, taskId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = "Creating CIFS Share : " + smbShare.getName() + " for Snapshot :" + fileObj.getLabel();
            successMessage = "CIFS Share creation for Snapshot :" + fileObj.getLabel() + " finished successfully";
            opName = ResourceOperationTypeEnum.CREATE_FILE_SNAPSHOT_SHARE.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, CREATE_FILESYSTEM_CIFS_SHARE_WF_NAME, false, taskId);
            String shareStep = workflow.createStepId();
            Object[] args = new Object[] { storageSystem, uri, smbShare };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_SHARE_METHOD, shareStep, stepDescription, storageSystem,
                    args);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error("Could not create CIFS share for filesystem/Snapshot: " + uri + " " + fileObj.getLabel(), ex);
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fileObj.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void createNFSExport(URI storage, URI uri, List<FileShareExport> exports, String opId) throws ControllerException {
        FileObject fileObj = null;
        String stepDescription = null;
        String successMessage = null;
        String opName = null;
        TaskCompleter completer = null;

        if (URIUtil.isType(uri, FileShare.class)) {
            completer = new FileWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(FileShare.class, uri);
            stepDescription = "Creating NFS Export for File System :" + fileObj.getLabel();
            successMessage = "NFS Export creation for FileSystem :" + fileObj.getLabel() + " finished successfully";
            opName = ResourceOperationTypeEnum.EXPORT_FILE_SYSTEM.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = "Creating NFS Export for FileSystem Snapshot :" + fileObj.getLabel();
            successMessage = "NFS Export creation for FileSystem Snapshot :" + fileObj.getLabel() + " finished successfully";
            opName = ResourceOperationTypeEnum.EXPORT_FILE_SNAPSHOT.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, CREATE_FILESYSTEM_NFS_EXPORT_WF_NAME, false, opId);
            String exportStep = workflow.createStepId();
            Object[] args = new Object[] { storage, uri, exports };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_EXPORT_METHOD, exportStep, stepDescription, storage, args);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error("Could not create NFS Export for filesystem/Snapshot: " + uri + " " + fileObj.getLabel(), ex);
            ServiceError serviceError = DeviceControllerException.errors.exportFileShareFailed(
                    fileObj.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void updateExportRules(URI storage, URI uri, FileExportUpdateParams param, String opId) throws ControllerException {
        FileObject fileObj = null;
        String stepDescription = null;
        String successMessage = null;
        String opName = null;
        TaskCompleter completer = null;

        if (URIUtil.isType(uri, FileShare.class)) {
            completer = new FileWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(FileShare.class, uri);
            stepDescription = "Updating File System : " + fileObj.getLabel() + " Export Rules: " + param.toString();
            successMessage = "File System: " + fileObj.getLabel() + " Export Rule updated successfully";
            opName = ResourceOperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SYSTEM.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = "Updating FileSystem Snapshot : " + fileObj.getLabel() + " Export Rules: " + param.toString();
            successMessage = "FileSystem Snapshot : " + fileObj.getLabel() + " Export Rule updated successfully";
            opName = ResourceOperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SNAPSHOT.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_FILESYSTEM_EXPORT_RULES_WF_NAME, false, opId);
            String exportRuleUpdateStep = workflow.createStepId();
            Object[] args = new Object[] { storage, uri, param };
            _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_EXPORT_RULES_METHOD, exportRuleUpdateStep, stepDescription,
                    storage, args);
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not update NFS Export Rules for filesystem/Snapshot: " + uri + " " + fileObj.getLabel(), ex);
            ServiceError serviceError = DeviceControllerException.errors.updateFileShareExportRulesFailed(
                    fileObj.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void updateShareACLs(URI storage, URI uri, String shareName, CifsShareACLUpdateParams param, String opId)
            throws ControllerException {
        FileObject fileObj = null;
        String stepDescription = null;
        String successMessage = null;
        String opName = null;
        TaskCompleter completer = null;

        if (URIUtil.isType(uri, FileShare.class)) {
            completer = new FileWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(FileShare.class, uri);
            stepDescription = "Updating File System : " + fileObj.getLabel() + " CIFS Share: " + shareName + " ACLs " + param.toString();
            successMessage = "File System: " + fileObj.getLabel() + " CIFS Share ACLs updated successfully";
            opName = ResourceOperationTypeEnum.UPDATE_FILE_SYSTEM_SHARE_ACL.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = "Updating FileSystem Snapshot : " + fileObj.getLabel() + " CIFS Share: " + shareName + " ACLs "
                    + param.toString();
            successMessage = "File System Snapshot : " + fileObj.getLabel() + " CIFS Share ACLs updated successfully";
            opName = ResourceOperationTypeEnum.UPDATE_FILE_SNAPSHOT_SHARE_ACL.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_FILESYSTEM_CIFS_SHARE_ACLS_WF_NAME, false, opId);
            String shareACLUpdateStep = workflow.createStepId();
            Object[] args = new Object[] { storage, uri, shareName, param };
            _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_SHARE_ACLS_METHOD, shareACLUpdateStep, stepDescription,
                    storage,
                    args);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error("Could not update CIFS Share ACLs for filesystem/Snapshot: " + uri + " " + fileObj.getLabel(), ex);
            ServiceError serviceError = DeviceControllerException.errors.updateFileShareCIFSACLsFailed(
                    fileObj.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void snapshotFS(URI storage, URI snapshot, URI fsURI, String opId) throws ControllerException {
        FileShare fileShare = s_dbClient.queryObject(FileShare.class, fsURI);
        // Using VNXeFSSnapshotTaskCompleter as it will serve the purpose..
        VNXeFSSnapshotTaskCompleter completer = new VNXeFSSnapshotTaskCompleter(Snapshot.class, snapshot, opId);
        Workflow workflow = null;
        try {
            workflow = _workflowService.getNewWorkflow(this, CREATE_FILESYSTEM_SNAPSHOT_WF_NAME, false, opId);
            String snapshotFSStep = workflow.createStepId();
            String stepDescription = "Creating File System Snapshot :" + fileShare.getLabel();
            Object[] args = new Object[] { storage, snapshot, fsURI };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_SNAPSHOT_METHOD, snapshotFSStep, stepDescription, storage,
                    args);
            String successMessage = "File System: " + fileShare.getLabel() + " snapshot " + snapshot.toString() + " created successfully";
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not create snapshot for filesystem: " + fsURI + " " + fileShare.getLabel(), ex);
            String opName = ResourceOperationTypeEnum.CREATE_FILE_SYSTEM_SNAPSHOT.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSystemSnapshotFailed(fsURI.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void deleteShare(URI storage, URI uri, FileSMBShare fileSMBShare, String opId) throws ControllerException {
        FileObject fileObj = null;
        String stepDescription = null;
        String successMessage = null;
        String opName = null;
        TaskCompleter completer = null;

        if (URIUtil.isType(uri, FileShare.class)) {
            completer = new FileWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(FileShare.class, uri);
            stepDescription = "Deleting CIFS Share : " + fileSMBShare.getName() + " for FileSystem : " + fileObj.getLabel();
            successMessage = "Deleting CIFS Share : " + fileSMBShare.getName() + " for FileSystem : " + fileObj.getLabel()
                    + " finished successfully.";
            opName = ResourceOperationTypeEnum.DELETE_FILE_SYSTEM_SHARE.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = "Deleting CIFS Share : " + fileSMBShare.getName() + " for FileSystem Snapshot : " + fileObj.getLabel();
            successMessage = "Deleting CIFS Share : " + fileSMBShare.getName() + " for FileSystem Snapshot : " + fileObj.getLabel()
                    + " finished successfully.";
            opName = ResourceOperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, DELETE_FILESYSTEM_CIFS_SHARE_WF_NAME, false, opId);
            String sharedeleteStep = workflow.createStepId();
            Object[] args = new Object[] { storage, uri, fileSMBShare };
            _fileDeviceController.createMethod(workflow, null, DELETE_FILESYSTEM_SHARE_METHOD, sharedeleteStep, stepDescription, storage,
                    args);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error("Could not delete CIFS Share for filesystem/Snapshot: " + uri + " " + fileObj.getLabel(), ex);
            ServiceError serviceError = DeviceControllerException.errors.deleteCIFSShareFailed(fileObj.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void deleteExportRules(URI storage, URI uri, boolean allDirs, String subDirs, String opId) throws ControllerException {
        FileObject fileObj = null;
        String stepDescription = null;
        String successMessage = null;
        String opName = null;
        TaskCompleter completer = null;

        if (URIUtil.isType(uri, FileShare.class)) {
            completer = new FileWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(FileShare.class, uri);
            stepDescription = "Deleting Export Rules for File System : " + fileObj.getLabel();
            successMessage = "Deleting Export Rules for File System : " + fileObj.getLabel() + " finished successfully.";
            opName = ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = "Deleting Export Rules for File System Snapshot : " + fileObj.getLabel();
            successMessage = "Deleting Export Rules for File System Snapshot : " + fileObj.getLabel() + " finished successfully.";
            opName = ResourceOperationTypeEnum.UNEXPORT_FILE_SNAPSHOT.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, DELETE_FILESYSTEM_EXPORT_RULES_WF_NAME, false, opId);
            String exportdeleteStep = workflow.createStepId();
            Object[] args = new Object[] { storage, uri, allDirs, subDirs };
            _fileDeviceController.createMethod(workflow, null, DELETE_FILESYSTEM_EXPORT_RULES, exportdeleteStep, stepDescription, storage,
                    args);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error("Could not delete export rules for filesystem/Snapshot: " + uri + " " + fileObj.getLabel(), ex);
            ServiceError serviceError = DeviceControllerException.errors.deleteExportRuleFailed(fileObj.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }

    }
}
