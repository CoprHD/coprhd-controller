/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.filereplicationcontroller.FileReplicationDeviceController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
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
import com.emc.storageos.volumecontroller.impl.file.MirrorFileFailoverTaskCompleter;
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

    static final String FAILOVER_FILESYSTEMS_WF_NAME = "FAILOVER_FILESYSTEM_WORKFLOW";
    static final String FAILBACK_FILESYSTEMS_WF_NAME = "FAILBACK_FILESYSTEM_WORKFLOW";
    static final String REPLICATE_CIFS_SHARES_TO_TARGET_WF_NAME = "REPLICATE_CIFS_SHARES_TO_TARGET_WORKFLOW";
    static final String REPLICATE_CIFS_SHARE_ACLS_TO_TARGET_WF_NAME = "REPLICATE_CIFS_SHARE_ACLS_TO_TARGET_WORKFLOW";
    static final String REPLICATE_NFS_EXPORT_TO_TARGET_WF_NAME = "REPLICATE_NFS_EXPORT_TO_TARGET_WORFLOW";
    static final String REPLICATE_NFS_EXPORT_RULES_TO_TARGET_WF_NAME = "REPLICATE_NFS_EXPORT_RULES_TO_TARGET_WORFLOW";

    private static final String CREATE_FILESYSTEM_EXPORT_METHOD = "export";
    private static final String DELETE_FILESYSTEM_EXPORT_METHOD = "unexport";
    private static final String CREATE_FILESYSTEM_SHARE_METHOD = "share";
    private static final String UPDATE_FILESYSTEM_SHARE_ACLS_METHOD = "updateShareACLs";
    private static final String UPDATE_FILESYSTEM_EXPORT_RULES_METHOD = "updateExportRules";
    private static final String CREATE_FILESYSTEM_SNAPSHOT_METHOD = "snapshotFS";
    private static final String DELETE_FILESYSTEM_SHARE_METHOD = "deleteShare";
    private static final String DELETE_FILESYSTEM_EXPORT_RULES = "deleteExportRules";
    private static final String FAILOVER_FILE_SYSTEM_METHOD = "failoverFileSystem";
    private static final String FAILBACK_FILE_SYSTEM_METHOD = "doFailBackMirrorSessionWF";
    private static final String REPLICATE_FILESYSTEM_CIFS_SHARES_METHOD = "addStepsToReplicateCIFSShares";
    private static final String REPLICATE_FILESYSTEM_CIFS_SHARE_ACLS_METHOD = "addStepsToReplicateCIFSShareACLs";
    private static final String REPLICATE_FILESYSTEM_NFS_EXPORT_METHOD = "addStepsToReplicateNFSExports";
    private static final String REPLICATE_FILESYSTEM_NFS_EXPORT_RULE_METHOD = "addStepsToReplicateNFSExportRules";

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController#createFileSystems(java.util.List,
     * java.lang.String)
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
            String waitFor = null; // the wait for key returned by previous call

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
     * @see
     * com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController#changeFileSystemVirtualPool(java.util.
     * List,
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
            String waitFor = null; // the wait for key returned by previous call

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
     * @see com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController#deleteFileSystems(java.util.List,
     * java.lang.String)
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
        String waitFor = null; // the wait for key returned by previous call
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
     * @see com.emc.storageos.fileorchestrationcontroller.FileOrchestrationController#expandFileSystem(java.net.URI,
     * long, java.lang.String)
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
        String waitFor = null; // the wait for key returned by previous call
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
            Workflow workflow = _workflowService.getNewWorkflow(this, CREATE_FILESYSTEM_CIFS_SHARE_WF_NAME, false, taskId, completer);
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
            Workflow workflow = _workflowService.getNewWorkflow(this, CREATE_FILESYSTEM_NFS_EXPORT_WF_NAME, false, opId, completer);
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
            Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_FILESYSTEM_EXPORT_RULES_WF_NAME, false, opId, completer);
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
            Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_FILESYSTEM_CIFS_SHARE_ACLS_WF_NAME, false, opId, completer);
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
            workflow = _workflowService.getNewWorkflow(this, CREATE_FILESYSTEM_SNAPSHOT_WF_NAME, false, opId, completer);
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
            Workflow workflow = _workflowService.getNewWorkflow(this, DELETE_FILESYSTEM_CIFS_SHARE_WF_NAME, false, opId, completer);
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
            Workflow workflow = _workflowService.getNewWorkflow(this, DELETE_FILESYSTEM_EXPORT_RULES_WF_NAME, false, opId, completer);
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

    @Override
    public void failbackFileSystem(URI fsURI, StoragePort nfsPort, StoragePort cifsPort, boolean replicateConfiguration, String taskId)
            throws ControllerException {
        FileWorkflowCompleter completer = new FileWorkflowCompleter(fsURI, taskId);
        Workflow workflow = null;
        String stepDescription = null;
        try {
            FileShare sourceFileShare = s_dbClient.queryObject(FileShare.class, fsURI);
            List<String> targetfileUris = new ArrayList<String>();
            targetfileUris.addAll(sourceFileShare.getMirrorfsTargets());
            FileShare targetFileShare = s_dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            StorageSystem systemSource = s_dbClient.queryObject(StorageSystem.class, sourceFileShare.getStorageDevice());

            workflow = this._workflowService.getNewWorkflow(this, FAILBACK_FILESYSTEMS_WF_NAME, false, taskId, completer);

            // Failback from Target File System

            s_logger.info("Generating steps for Failback Source File System from Target");
            String failbackStep = workflow.createStepId();
            stepDescription = String.format("Failback to source file System : %s from target system : %s.", sourceFileShare.getName(),
                    targetFileShare.getName());
            Object[] args = new Object[] { systemSource.getId(), sourceFileShare.getId() };
            String waitForFailback = _fileReplicationDeviceController.createMethod(workflow, null, null,
                    FAILBACK_FILE_SYSTEM_METHOD, failbackStep, stepDescription, systemSource.getId(), args);

            if (replicateConfiguration) {
                // Replicate NFS export and rules to Target Cluster.
                FSExportMap targetnfsExportMap = targetFileShare.getFsExports();
                FSExportMap sourcenfsExportMap = sourceFileShare.getFsExports();

                if (targetnfsExportMap != null && sourcenfsExportMap != null) {

                    stepDescription = String.format("Replicating NFS exports from target file system : %s to source file system : %s",
                            targetFileShare.getId(), sourceFileShare.getId());
                    Workflow.Method replicateNFSExportMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_EXPORT_METHOD,
                            systemSource.getId(), targetFileShare.getId(), nfsPort);
                    String replicateNFSExportStep = workflow.createStepId();

                    String waitForExport = workflow.createStep(null, stepDescription, waitForFailback, systemSource.getId(),
                            systemSource.getSystemType(), getClass(), replicateNFSExportMethod, null, replicateNFSExportStep);

                    stepDescription = String.format("Replicating NFS export rules from target file system : %s to source file system : %s",
                            targetFileShare.getId(), sourceFileShare.getId());
                    Workflow.Method replicateNFSExportRulesMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_EXPORT_RULE_METHOD,
                            systemSource.getId(), targetFileShare.getId());
                    String replicateNFSExportRulesStep = workflow.createStepId();

                    workflow.createStep(null, stepDescription, waitForExport, systemSource.getId(), systemSource.getSystemType(),
                            getClass(), replicateNFSExportRulesMethod, null, replicateNFSExportRulesStep);
                }
                // Replicate CIFS shares and ACLs from Target File System to Source.

                SMBShareMap targetSMBShareMap = targetFileShare.getSMBFileShares();
                SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();

                if (targetSMBShareMap != null && sourceSMBShareMap != null) {

                    stepDescription = String.format("Replicating CIFS shares from target file system : %s to file source system : %s",
                            targetFileShare.getId(), sourceFileShare.getId());
                    Workflow.Method replicateCIFSShareMethod = new Workflow.Method(REPLICATE_FILESYSTEM_CIFS_SHARES_METHOD,
                            systemSource.getId(), targetFileShare.getId(), cifsPort);
                    String replicateCIFSShareStep = workflow.createStepId();

                    String waitForShare = workflow.createStep(null, stepDescription, waitForFailback, systemSource.getId(),
                            systemSource.getSystemType(), getClass(), replicateCIFSShareMethod, null,
                            replicateCIFSShareStep);

                    stepDescription = String.format("Replicating CIFS share ACLs from target file system : %s to source file system : %s",
                            targetFileShare.getId(), sourceFileShare.getId());
                    Workflow.Method replicateCIFSShareACLsMethod = new Workflow.Method(REPLICATE_FILESYSTEM_CIFS_SHARE_ACLS_METHOD,
                            systemSource.getId(), targetFileShare.getId());
                    String replicateCIFSShareACLsStep = workflow.createStepId();

                    workflow.createStep(null, stepDescription, waitForShare, systemSource.getId(), systemSource.getSystemType(),
                            getClass(), replicateCIFSShareACLsMethod, null, replicateCIFSShareACLsStep);
                }
            }
            String successMessage = "Failback FileSystem successful for: " + sourceFileShare.getLabel();
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error("Could not failback filesystems: " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILBACK.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsURI.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    @Override
    public void failoverFileSystem(URI fsURI, StoragePort nfsPort, StoragePort cifsPort, boolean replicateConfiguration, String taskId)
            throws ControllerException {

        FileWorkflowCompleter completer = new FileWorkflowCompleter(fsURI, taskId);
        Workflow workflow = null;
        String stepDescription = null;
        try {

            FileShare sourceFileShare = s_dbClient.queryObject(FileShare.class, fsURI);
            List<String> targetfileUris = new ArrayList<String>();
            targetfileUris.addAll(sourceFileShare.getMirrorfsTargets());
            FileShare targetFileShare = s_dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            StorageSystem systemTarget = s_dbClient.queryObject(StorageSystem.class, targetFileShare.getStorageDevice());

            workflow = this._workflowService.getNewWorkflow(this, FAILOVER_FILESYSTEMS_WF_NAME, false, taskId, completer);

            // Failover File System to Target

            s_logger.info("Generating steps for Failover File System to Target");
            String failoverStep = workflow.createStepId();
            MirrorFileFailoverTaskCompleter failoverCompleter = new MirrorFileFailoverTaskCompleter(sourceFileShare.getId(),
                    targetFileShare.getId(), failoverStep);
            stepDescription = String.format("Failover source file System : %s to target system : %s.", sourceFileShare.getName(),
                    targetFileShare.getName());
            Object[] args = new Object[] { systemTarget.getId(), targetFileShare.getId(), failoverCompleter };
            String waitForFailover = _fileReplicationDeviceController.createMethod(workflow, null, null,
                    FAILOVER_FILE_SYSTEM_METHOD, failoverStep, stepDescription, systemTarget.getId(), args);

            if (replicateConfiguration) {
                // Replicate CIFS shares and ACLs to Target Cluster.
                SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();
                SMBShareMap targetSMBShareMap = targetFileShare.getSMBFileShares();

                if (sourceSMBShareMap != null || targetSMBShareMap != null) {

                    stepDescription = String.format("Replicating CIFS shares from source file system : %s to target file system : %s",
                            sourceFileShare.getId(), targetFileShare.getId());
                    Workflow.Method replicateCIFSShareMethod = new Workflow.Method(REPLICATE_FILESYSTEM_CIFS_SHARES_METHOD,
                            systemTarget.getId(), fsURI, cifsPort);
                    String replicateCIFSShareStep = workflow.createStepId();
                    String waitForShare = workflow.createStep(null, stepDescription, waitForFailover, systemTarget.getId(),
                            systemTarget.getSystemType(), getClass(), replicateCIFSShareMethod, null, replicateCIFSShareStep);

                    stepDescription = String.format("Replicating CIFS share ACLs from source file system : %s to file target system : %s",
                            sourceFileShare.getId(), targetFileShare.getId());
                    Workflow.Method replicateCIFSShareACLsMethod = new Workflow.Method(REPLICATE_FILESYSTEM_CIFS_SHARE_ACLS_METHOD,
                            systemTarget.getId(), fsURI);
                    String replicateCIFSShareACLsStep = workflow.createStepId();
                    workflow.createStep(null, stepDescription, waitForShare, systemTarget.getId(),
                            systemTarget.getSystemType(), getClass(), replicateCIFSShareACLsMethod, null, replicateCIFSShareACLsStep);
                }

                // Replicate NFS export and rules to Target Cluster.
                FSExportMap sourceNFSExportMap = sourceFileShare.getFsExports();
                FSExportMap targetNFSExportMap = targetFileShare.getFsExports();

                if (sourceNFSExportMap != null || targetNFSExportMap != null) {

                    stepDescription = String.format("Replicating NFS exports from source file system : %s to target file system : %s",
                            sourceFileShare.getId(), targetFileShare.getId());
                    Workflow.Method replicateNFSExportMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_EXPORT_METHOD,
                            systemTarget.getId(), fsURI, nfsPort);
                    String replicateNFSExportStep = workflow.createStepId();
                    String waitForExport = workflow.createStep(null, stepDescription, waitForFailover, systemTarget.getId(),
                            systemTarget.getSystemType(), getClass(), replicateNFSExportMethod, null, replicateNFSExportStep);

                    stepDescription = String.format("Replicating NFS export rules from source file system : %s to target file system : %s",
                            sourceFileShare.getId(), targetFileShare.getId());
                    Workflow.Method replicateNFSExportRulesMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_EXPORT_RULE_METHOD,
                            systemTarget.getId(), fsURI);
                    String replicateNFSExportRulesStep = workflow.createStepId();
                    workflow.createStep(null, stepDescription, waitForExport, systemTarget.getId(), systemTarget.getSystemType(),
                            getClass(), replicateNFSExportRulesMethod, null, replicateNFSExportRulesStep);
                }
            }
            String successMessage = "Failover FileSystem successful for: " + sourceFileShare.getLabel();
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error("Could not failover filesystems: " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsURI.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    /**
     * Child workflow for replicating source file system CIFS shares to target.
     * 
     * @param systemTarget - URI of target StorageSystem where source CIFS shares has to be replicated.
     * @param fsURI -URI of the source FileSystem
     * @param cifsPort -StoragePort, CIFS port of target File System where new shares has to be created.
     * @param taskId
     */
    public void addStepsToReplicateCIFSShares(URI systemTarget, URI fsURI, StoragePort cifsPort, String taskId) {
        s_logger.info("Generating steps for Replicating CIFS shares to Target Cluster");
        FileWorkflowCompleter completer = new FileWorkflowCompleter(fsURI, taskId);
        Workflow workflow = null;
        FileShare targetFileShare = null;
        try {
            FileShare sourceFileShare = s_dbClient.queryObject(FileShare.class, fsURI);
            if (sourceFileShare.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
                List<String> targetfileUris = new ArrayList<String>();
                targetfileUris.addAll(sourceFileShare.getMirrorfsTargets());
                targetFileShare = s_dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            } else {
                targetFileShare = s_dbClient.queryObject(FileShare.class, sourceFileShare.getParentFileShare());
            }

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_CIFS_SHARES_TO_TARGET_WF_NAME, false, taskId, completer);

            SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();
            SMBShareMap targetSMBShareMap = targetFileShare.getSMBFileShares();

            if (sourceSMBShareMap == null) {
                List<SMBFileShare> targetSMBShares = new ArrayList<SMBFileShare>(targetSMBShareMap.values());
                deleteCIFSShareFromTarget(workflow, systemTarget, targetSMBShares, targetFileShare);

            } else if (targetSMBShareMap == null) {
                List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());
                createCIFSShareOnTarget(workflow, systemTarget, sourceSMBShares, cifsPort, targetFileShare, sourceFileShare);

            } else {
                List<SMBFileShare> targetSMBSharestoDelete = new ArrayList<SMBFileShare>();
                List<SMBFileShare> targetSMBSharestoCreate = new ArrayList<SMBFileShare>();

                for (String sourceSMBSharesName : sourceSMBShareMap.keySet()) {
                    if (targetSMBShareMap.get(sourceSMBSharesName) == null) {
                        targetSMBSharestoCreate.add(sourceSMBShareMap.get(sourceSMBSharesName));
                    }
                }
                for (String targetSMBSharesName : targetSMBShareMap.keySet()) {
                    if (sourceSMBShareMap.get(targetSMBSharesName) == null) {
                        targetSMBSharestoDelete.add(targetSMBShareMap.get(targetSMBSharesName));
                    }
                }
                if (!targetSMBSharestoCreate.isEmpty()) {
                    createCIFSShareOnTarget(workflow, systemTarget, targetSMBSharestoCreate, cifsPort, targetFileShare, sourceFileShare);
                }
                if (!targetSMBSharestoDelete.isEmpty()) {
                    deleteCIFSShareFromTarget(workflow, systemTarget, targetSMBSharestoDelete, targetFileShare);
                }
            }
            String successMessage = String.format(
                    "Replicating source File System : %s, CIFS Shares to Target System finished successfully", sourceFileShare.getLabel());
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem CIFS shares: " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsURI.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    /**
     * Child workflow for replicating source file system CIFS ACLs to target system.
     * 
     * @param systemTarget - URI of target StorageSystem where source CIFS shares has to be replicated.
     * @param fsURI -URI of the source FileSystem
     * @param taskId
     */
    public void addStepsToReplicateCIFSShareACLs(URI systemTarget, URI fsURI, String taskId) {
        s_logger.info("Generating steps for Replicating CIFS share ACLs to Target Cluster");
        CifsShareACLUpdateParams params;
        FileWorkflowCompleter completer = new FileWorkflowCompleter(fsURI, taskId);
        FileShare targetFileShare = null;
        Workflow workflow = null;
        try {

            FileShare sourceFileShare = s_dbClient.queryObject(FileShare.class, fsURI);
            if (sourceFileShare.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
                List<String> targetfileUris = new ArrayList<String>();
                targetfileUris.addAll(sourceFileShare.getMirrorfsTargets());
                targetFileShare = s_dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            } else {
                targetFileShare = s_dbClient.queryObject(FileShare.class, sourceFileShare.getParentFileShare());
            }

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_CIFS_SHARE_ACLS_TO_TARGET_WF_NAME, false, taskId, completer);
            SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();

            if (sourceSMBShareMap != null) {
                List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());

                for (SMBFileShare sourceSMBShare : sourceSMBShares) {

                    List<ShareACL> sourceShareACLs = FileOrchestrationUtils.queryShareACLs(sourceSMBShare.getName(),
                            sourceFileShare.getId(), s_dbClient);
                    List<ShareACL> targetShareACLs = FileOrchestrationUtils.queryShareACLs(sourceSMBShare.getName(),
                            targetFileShare.getId(), s_dbClient);

                    if (sourceShareACLs != null && !sourceShareACLs.isEmpty()) {

                        if (targetShareACLs.isEmpty()) {
                            params = new CifsShareACLUpdateParams();
                            ShareACLs shareACLs = new ShareACLs();
                            shareACLs.setShareACLs(sourceShareACLs);
                            params.setAclsToAdd(shareACLs);
                            updateCIFSShareACLOnTarget(workflow, systemTarget, targetFileShare, sourceSMBShare, params);

                        } else {

                            List<ShareACL> shareACLsToAdd = new ArrayList<ShareACL>();
                            List<ShareACL> shareACLsToDelete = new ArrayList<ShareACL>();
                            List<ShareACL> shareACLsToModify = new ArrayList<ShareACL>();
                            HashMap<String, ShareACL> sourceShareACLMap = FileOrchestrationUtils.getShareACLMap(sourceShareACLs);
                            HashMap<String, ShareACL> targetShareACLMap = FileOrchestrationUtils.getShareACLMap(targetShareACLs);

                            // ACLs To Add
                            for (String sourceACLName : sourceShareACLMap.keySet()) {
                                if (targetShareACLMap.get(sourceACLName) == null) {
                                    ShareACL shareACL = sourceShareACLMap.get(sourceACLName);
                                    shareACL.setFileSystemId(targetFileShare.getId());
                                    shareACLsToAdd.add(shareACL);
                                }
                            }

                            // ACLs To Delete
                            for (String targetACLName : targetShareACLMap.keySet()) {
                                if (sourceShareACLMap.get(targetACLName) == null) {
                                    shareACLsToDelete.add(targetShareACLMap.get(targetACLName));
                                }
                            }

                            // ACLs to Modify
                            targetShareACLs.removeAll(shareACLsToDelete);
                            sourceShareACLs.removeAll(shareACLsToAdd);
                            sourceShareACLMap = FileOrchestrationUtils.getShareACLMap(sourceShareACLs);
                            targetShareACLMap = FileOrchestrationUtils.getShareACLMap(targetShareACLs);

                            for (String sourceACLName : sourceShareACLMap.keySet()) {
                                if (targetShareACLMap.get(sourceACLName) != null && !targetShareACLMap.get(sourceACLName).getPermission()
                                        .equals(sourceShareACLMap.get(sourceACLName).getPermission())) {
                                    ShareACL shareACL = targetShareACLMap.get(sourceACLName);
                                    shareACL.setPermission(sourceShareACLMap.get(sourceACLName).getPermission());
                                    shareACLsToModify.add(shareACL);
                                }
                            }

                            params = new CifsShareACLUpdateParams();

                            if (!shareACLsToAdd.isEmpty()) {
                                ShareACLs addShareACLs = new ShareACLs();
                                addShareACLs.setShareACLs(shareACLsToAdd);
                                params.setAclsToAdd(addShareACLs);
                            }
                            if (!shareACLsToDelete.isEmpty()) {
                                ShareACLs deleteShareACLs = new ShareACLs();
                                deleteShareACLs.setShareACLs(shareACLsToDelete);
                                params.setAclsToDelete(deleteShareACLs);
                            }
                            if (!shareACLsToModify.isEmpty()) {
                                ShareACLs modifyShareACLs = new ShareACLs();
                                modifyShareACLs.setShareACLs(shareACLsToModify);
                                params.setAclsToModify(modifyShareACLs);
                            }

                            if (params.retrieveAllACLs() != null && !params.retrieveAllACLs().isEmpty()) {
                                updateCIFSShareACLOnTarget(workflow, systemTarget, targetFileShare, sourceSMBShare, params);
                            }
                        }
                    }
                }
            }
            String successMessage = String.format(
                    "Replicating source File System : %s, CIFS Shares ACLs to Target System finished successfully",
                    sourceFileShare.getLabel());
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem CIFS shares ACLs : " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsURI.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    /**
     * Child workflow for replicating source file system NFS export to target.
     * 
     * @param systemTarget - URI of target StorageSystem where source NFS shares has to be replicated.
     * @param fsURI -URI of the source FileSystem
     * @param nfsPort -StoragePort, NFS port of target File System where new export has to be created.
     * @param taskId
     */
    public void addStepsToReplicateNFSExports(URI systemTarget, URI fsURI, StoragePort nfsPort, String taskId) {
        s_logger.info("Generating steps for Replicating NFS exports to Target Cluster");
        FileWorkflowCompleter completer = new FileWorkflowCompleter(fsURI, taskId);
        Workflow workflow = null;
        FileShare targetFileShare = null;
        try {
            FileShare sourceFileShare = s_dbClient.queryObject(FileShare.class, fsURI);
            if (sourceFileShare.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
                List<String> targetfileUris = new ArrayList<String>();
                targetfileUris.addAll(sourceFileShare.getMirrorfsTargets());
                targetFileShare = s_dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            } else {
                targetFileShare = s_dbClient.queryObject(FileShare.class, sourceFileShare.getParentFileShare());
            }

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_NFS_EXPORT_TO_TARGET_WF_NAME, false, taskId, completer);

            FSExportMap sourceNFSExportMap = sourceFileShare.getFsExports();
            FSExportMap targetNFSExportMap = targetFileShare.getFsExports();

            if (sourceNFSExportMap == null) {
                List<FileExport> targetNFSExports = new ArrayList<FileExport>(targetNFSExportMap.values());
                deleteNFSExportFromTarget(workflow, systemTarget, targetNFSExports, targetFileShare);

            } else if (targetNFSExportMap == null) {
                List<FileExport> sourceNFSExports = new ArrayList<FileExport>(sourceNFSExportMap.values());
                createNFSExportOnTarget(workflow, systemTarget, sourceNFSExports, nfsPort, targetFileShare, sourceFileShare);

            } else {

                List<FileExport> sourceNFSExports = new ArrayList<FileExport>(sourceNFSExportMap.values());
                List<FileExport> targetNFSExports = new ArrayList<FileExport>(targetNFSExportMap.values());

                List<FileExport> targetNFSExportstoCreate = new ArrayList<FileExport>();
                List<FileExport> targetNFSExportstoDelete = new ArrayList<FileExport>();

                // Creating new map since FSExportMap key contains path+sec+user
                HashMap<String, FileExport> sourceFileExportMap = FileOrchestrationUtils.getFileExportMap(sourceNFSExports);
                HashMap<String, FileExport> targetFileExportMap = FileOrchestrationUtils.getFileExportMap(targetNFSExports);

                for (String exportPath : sourceFileExportMap.keySet()) {
                    if (exportPath.equals(sourceFileShare.getPath())) {
                        if (targetFileExportMap.get(targetFileShare.getPath()) == null) {
                            targetNFSExportstoCreate.add(sourceFileExportMap.get(exportPath));
                        }
                    } else {
                        ArrayList<String> subdirName = new ArrayList<String>();
                        subdirName.add(exportPath.split(sourceFileShare.getPath())[1]);
                        if (targetFileExportMap.get(targetFileShare.getPath() + subdirName.get(0)) == null) {
                            targetNFSExportstoCreate.add(sourceFileExportMap.get(exportPath));
                        }
                    }
                }

                for (String exportPath : targetFileExportMap.keySet()) {
                    if (exportPath.equals(targetFileShare.getPath())) {
                        if (sourceFileExportMap.get(sourceFileShare.getPath()) == null) {
                            targetNFSExportstoDelete.add(targetFileExportMap.get(exportPath));
                        }
                    } else {
                        ArrayList<String> subdirName = new ArrayList<String>();
                        subdirName.add(exportPath.split(targetFileShare.getPath())[1]);
                        if (sourceFileExportMap.get(sourceFileShare.getPath() + subdirName.get(0)) == null) {
                            targetNFSExportstoDelete.add(targetFileExportMap.get(exportPath));
                        }
                    }
                }

                if (!targetNFSExportstoCreate.isEmpty()) {
                    createNFSExportOnTarget(workflow, systemTarget, targetNFSExportstoCreate, nfsPort, targetFileShare, sourceFileShare);
                }
                if (!targetNFSExportstoDelete.isEmpty()) {
                    deleteNFSExportFromTarget(workflow, systemTarget, targetNFSExportstoDelete, targetFileShare);
                }
            }
            String successMessage = String.format(
                    "Replicating source File System : %s NFS Exports to Target System finished successfully", sourceFileShare.getId());
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem NFS Exports : " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsURI.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    /**
     * Child workflow for replicating source file system NFS export Rules to target.
     * 
     * @param systemTarget - URI of target StorageSystem where source NFS shares has to be replicated.
     * @param fsURI -URI of the source FileSystem
     * @param taskId
     */
    public void addStepsToReplicateNFSExportRules(URI systemTarget, URI fsURI, String taskId) {
        s_logger.info("Generating steps for Replicating NFS export rules to Target Cluster");
        FileWorkflowCompleter completer = new FileWorkflowCompleter(fsURI, taskId);
        Workflow workflow = null;
        FileShare targetFileShare = null;
        try {
            FileShare sourceFileShare = s_dbClient.queryObject(FileShare.class, fsURI);
            if (sourceFileShare.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
                List<String> targetfileUris = new ArrayList<String>();
                targetfileUris.addAll(sourceFileShare.getMirrorfsTargets());
                targetFileShare = s_dbClient.queryObject(FileShare.class, URI.create(targetfileUris.get(0)));
            } else {
                targetFileShare = s_dbClient.queryObject(FileShare.class, sourceFileShare.getParentFileShare());
            }

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_NFS_EXPORT_RULES_TO_TARGET_WF_NAME, false, taskId, completer);

            SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();

            if (sourceSMBShareMap != null) {

                HashMap<String, List<ExportRule>> sourceExportRuleMap = FileOrchestrationUtils.getFSExportRuleMap(sourceFileShare,
                        s_dbClient);
                HashMap<String, List<ExportRule>> targetExportRuleMap = FileOrchestrationUtils.getFSExportRuleMap(targetFileShare,
                        s_dbClient);

                for (String exportPath : sourceExportRuleMap.keySet()) {

                    FileExportUpdateParams params = new FileExportUpdateParams();
                    List<ExportRule> exportRulesToAdd = new ArrayList<ExportRule>();
                    List<ExportRule> exportRulesToDelete = new ArrayList<ExportRule>();
                    List<ExportRule> exportRulesToModify = new ArrayList<ExportRule>();
                    List<ExportRule> sourceExportRules;
                    List<ExportRule> targetExportRules;
                    HashMap<String, ExportRule> srcExportRuleSecFlvMap;
                    HashMap<String, ExportRule> trgtExportRuleSecFlvMap;

                    if (exportPath.equals(sourceFileShare.getPath())) {
                        // File system export rules....
                        sourceExportRules = sourceExportRuleMap.get(exportPath);
                        targetExportRules = targetExportRuleMap.get(targetFileShare.getPath());

                    } else {
                        // Sub directory export rules....
                        sourceExportRules = sourceExportRuleMap.get(exportPath);
                        String subDir = exportPath.split(sourceFileShare.getPath())[1];
                        targetExportRules = targetExportRuleMap.get(targetFileShare.getPath() + subDir);
                        params.setSubDir(subDir.substring(1));
                    }

                    srcExportRuleSecFlvMap = FileOrchestrationUtils.getExportRuleSecFlvMap(sourceExportRules);
                    trgtExportRuleSecFlvMap = FileOrchestrationUtils.getExportRuleSecFlvMap(targetExportRules);

                    FileOrchestrationUtils.checkForExportRuleToAdd(sourceFileShare, targetFileShare, srcExportRuleSecFlvMap,
                            trgtExportRuleSecFlvMap, exportRulesToAdd);

                    FileOrchestrationUtils.checkForExportRuleToDelete(srcExportRuleSecFlvMap, trgtExportRuleSecFlvMap, exportRulesToDelete);

                    sourceExportRules.removeAll(exportRulesToAdd);
                    targetExportRules.removeAll(exportRulesToDelete);
                    srcExportRuleSecFlvMap = FileOrchestrationUtils.getExportRuleSecFlvMap(sourceExportRules);
                    trgtExportRuleSecFlvMap = FileOrchestrationUtils.getExportRuleSecFlvMap(targetExportRules);

                    FileOrchestrationUtils.checkForExportRuleToModify(srcExportRuleSecFlvMap, trgtExportRuleSecFlvMap,
                            exportRulesToModify);

                    if (!exportRulesToAdd.isEmpty()) {
                        ExportRules addExportRules = new ExportRules();
                        addExportRules.setExportRules(exportRulesToAdd);
                        params.setExportRulesToAdd(addExportRules);
                    }
                    if (!exportRulesToDelete.isEmpty()) {
                        ExportRules deleteExportRules = new ExportRules();
                        deleteExportRules.setExportRules(exportRulesToDelete);
                        params.setExportRulesToDelete(deleteExportRules);
                    }
                    if (!exportRulesToModify.isEmpty()) {
                        ExportRules modifyExportRules = new ExportRules();
                        modifyExportRules.setExportRules(exportRulesToModify);
                        params.setExportRulesToModify(modifyExportRules);
                    }

                    if (params.retrieveAllExports() != null && !params.retrieveAllExports().isEmpty()) {
                        String stepDescription = String.format(
                                "updating NFS export rules for path : %s, %s", exportPath, params.toString());
                        String exportRuleUpdateStep = workflow.createStepId();
                        Object[] args = new Object[] { systemTarget, targetFileShare.getId(), params };
                        _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_EXPORT_RULES_METHOD, exportRuleUpdateStep,
                                stepDescription, systemTarget, args);
                    }
                }
            }
            String successMessage = String.format(
                    "Replicating source File System : %s, NFS Exports Rules to Target System finished successfully",
                    sourceFileShare.getLabel());
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem NFS Exports Rules : " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsURI.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    private static void createNFSExportOnTarget(Workflow workflow, URI systemTarget, List<FileExport> nfsExportsToCreate,
            StoragePort nfsPort, FileShare targetFileShare, FileShare sourceFileShare) {

        for (FileExport nfsExport : nfsExportsToCreate) {

            FileShareExport fileNFSExport = new FileShareExport(nfsExport.getClients(), nfsExport.getSecurityType(),
                    nfsExport.getPermissions(), nfsExport.getRootUserMapping(),
                    nfsExport.getProtocol(), nfsPort.getPortName(), nfsPort.getPortNetworkId(), null);

            if (!sourceFileShare.getPath().equals(nfsExport.getPath())) {
                ArrayList<String> subdirName = new ArrayList<String>();
                subdirName.add(nfsExport.getPath().split(sourceFileShare.getPath())[1]);
                fileNFSExport.setMountPath(targetFileShare.getMountPath() + subdirName.get(0));
                fileNFSExport.setPath(targetFileShare.getMountPath() + subdirName.get(0));
            } else {
                fileNFSExport.setMountPath(targetFileShare.getMountPath());
                fileNFSExport.setPath(targetFileShare.getMountPath());
            }
            String stepDescription = String.format("creating NFS export : %s", fileNFSExport.getMountPath());
            String exportCreationStep = workflow.createStepId();
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), Arrays.asList(fileNFSExport) };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_EXPORT_METHOD, exportCreationStep, stepDescription,
                    systemTarget, args);
        }
    }

    private static void deleteNFSExportFromTarget(Workflow workflow, URI systemTarget, List<FileExport> nfsExportsToDelete,
            FileShare targetFileShare) {
        for (FileExport nfsExport : nfsExportsToDelete) {
            FileShareExport fileNFSExport = new FileShareExport(nfsExport);
            String stepDescription = String.format("deleting NFS export : %s", fileNFSExport.getMountPath());
            String exportCreationStep = workflow.createStepId();
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), Arrays.asList(fileNFSExport) };
            _fileDeviceController.createMethod(workflow, null, DELETE_FILESYSTEM_EXPORT_METHOD, exportCreationStep, stepDescription,
                    systemTarget, args);
        }
    }

    private static void createCIFSShareOnTarget(Workflow workflow, URI systemTarget, List<SMBFileShare> smbShares, StoragePort cifsPort,
            FileShare targetFileShare, FileShare sourceFileShare) {

        for (SMBFileShare smbShare : smbShares) {
            FileSMBShare fileSMBShare = new FileSMBShare(smbShare);
            fileSMBShare.setStoragePortName(cifsPort.getPortName());
            fileSMBShare.setStoragePortNetworkId(cifsPort.getPortNetworkId());
            if (fileSMBShare.isSubDirPath()) {
                fileSMBShare.setPath(targetFileShare.getPath() + fileSMBShare.getPath().split(sourceFileShare.getPath())[1]);
            } else {
                fileSMBShare.setPath(targetFileShare.getPath());
            }
            String shareCreationStep = workflow.createStepId();
            String stepDescription = String.format("creating CIFS Share : %s, path : %s", fileSMBShare.getName(),
                    fileSMBShare.getPath());
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), fileSMBShare };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_SHARE_METHOD, shareCreationStep, stepDescription,
                    systemTarget, args);
        }
    }

    private static void deleteCIFSShareFromTarget(Workflow workflow, URI systemTarget, List<SMBFileShare> smbShares,
            FileShare targetFileShare) {
        for (SMBFileShare smbShare : smbShares) {
            FileSMBShare fileSMBShare = new FileSMBShare(smbShare);
            String stepDescription = String.format("deleting CIFS share : %s, path : %s", fileSMBShare.getName(), fileSMBShare.getPath());
            String sharedeleteStep = workflow.createStepId();
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), fileSMBShare };
            _fileDeviceController.createMethod(workflow, null, DELETE_FILESYSTEM_SHARE_METHOD, sharedeleteStep,
                    stepDescription, systemTarget, args);
        }
    }

    private static void updateCIFSShareACLOnTarget(Workflow workflow, URI systemTarget, FileShare targetFileShare,
            SMBFileShare sourceSMBShare, CifsShareACLUpdateParams params) {
        String stepDescription = String.format(
                "updating CIFS share : %s, ACLs : %s", sourceSMBShare.getName(), params.toString());
        String shareACLUpdateStep = workflow.createStepId();
        Object[] args = new Object[] { systemTarget, targetFileShare.getId(), sourceSMBShare.getName(), params };
        _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_SHARE_ACLS_METHOD, shareACLUpdateStep,
                stepDescription, systemTarget, args);
    }
}
