/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.filereplicationcontroller.FileReplicationDeviceController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.MountInfo;
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

    private static final String UNMOUNT_FILESYSTEM_EXPORT_METHOD = "unmountDevice";
    private static final String VERIFY_MOUNT_DEPENDENCIES_METHOD = "verifyMountDependencies";
    private static final String IS_EXPORT_MOUNTED_METHOD = "isExportMounted";

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
    public void updateExportRules(URI storage, URI uri, FileExportUpdateParams param, boolean unmountExport, String opId)
            throws ControllerException {
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
            String waitFor = null;
            // Check if the export should be unmounted before deleting
            if (unmountExport == true) {
                // get all the mounts and generate steps for unmounting them
                List<MountInfo> mountList = _fileDeviceController.getMountedExports(uri, param.getSubDir(), param);
                for (MountInfo mount : mountList) {
                    Object[] args = new Object[] { mount.getHostId(), mount.getFsId(), mount.getMountPath() };
                    waitFor = _fileDeviceController.createMethod(workflow, waitFor, UNMOUNT_FILESYSTEM_EXPORT_METHOD, null,
                            "Unmounting path:" + mount.getMountPath(), storage, args);
                }
            } else if (URIUtil.isType(uri, FileShare.class)) {
                // Check if the export rule is mounted and throw an error if mounted
                Object[] args = new Object[] { uri, param };
                waitFor = _fileDeviceController.createMethod(workflow, waitFor, VERIFY_MOUNT_DEPENDENCIES_METHOD,
                        null, "Verifying mount dependencies", storage, args);
            }
            Object[] args = new Object[] { storage, uri, param };
            _fileDeviceController.createMethod(workflow, waitFor, UPDATE_FILESYSTEM_EXPORT_RULES_METHOD, null, stepDescription,
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
    public void deleteExportRules(URI storage, URI uri, boolean allDirs, String subDirs, boolean unmountExport, String opId)
            throws ControllerException {
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
            String waitFor = null;
            // Check if the export should be unmounted before deleting
            if (unmountExport == true) {
                // get all the mounts and generate steps for unmounting them
                List<MountInfo> mountList = _fileDeviceController.getAllMountedExports(uri, subDirs, allDirs);
                for (MountInfo mount : mountList) {
                    Object[] args = new Object[] { mount.getHostId(), mount.getFsId(), mount.getMountPath() };
                    waitFor = _fileDeviceController.createMethod(workflow, waitFor, UNMOUNT_FILESYSTEM_EXPORT_METHOD, null,
                            "Unmounting path:" + mount.getMountPath(), storage, args);
                }
            } else if (URIUtil.isType(uri, FileShare.class)) {
                // Check if the export is mounted and throw an error if mounted
                Object[] args = new Object[] { uri, subDirs, allDirs };
                waitFor = _fileDeviceController.createMethod(workflow, waitFor, IS_EXPORT_MOUNTED_METHOD, null,
                        "Checking if the export is mounted", storage, args);
            }
            Object[] args = new Object[] { storage, uri, allDirs, subDirs };
            _fileDeviceController.createMethod(workflow, waitFor, DELETE_FILESYSTEM_EXPORT_RULES, null, stepDescription, storage,
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

            workflow = this._workflowService.getNewWorkflow(this, FAILBACK_FILESYSTEMS_WF_NAME, false, taskId);

            // Failback from Target File System

            s_logger.info("Generating steps for Failback Source File System from Target");
            String failbackStep = workflow.createStepId();
            stepDescription = String.format("Failback To Source File System:  %s from Target File System", sourceFileShare);
            Object[] args = new Object[] { systemSource.getId(), sourceFileShare.getId() };
            String waitForFailback = _fileReplicationDeviceController.createMethod(workflow, null, null,
                    FAILBACK_FILE_SYSTEM_METHOD, failbackStep, stepDescription, systemSource.getId(), args);

            if (replicateConfiguration) {
                // Replicate NFS export and rules to Target Cluster.
                FSExportMap nfsExportMap = targetFileShare.getFsExports();

                if (nfsExportMap != null && nfsPort != null) {

                    stepDescription = "Replicating Target File System NFS Exports To Source Cluster";
                    Workflow.Method replicateNFSExportMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_EXPORT_METHOD,
                            systemSource.getId(), targetFileShare.getId(), nfsPort);
                    String replicateNFSExportStep = workflow.createStepId();

                    String waitForExport = workflow.createStep(null, stepDescription, waitForFailback, systemSource.getId(),
                            systemSource.getSystemType(), getClass(), replicateNFSExportMethod, null, replicateNFSExportStep);

                    stepDescription = "Replicating Target File System NFS Export Rules To Source Cluster";
                    Workflow.Method replicateNFSExportRulesMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_EXPORT_RULE_METHOD,
                            systemSource.getId(), targetFileShare.getId());
                    String replicateNFSExportRulesStep = workflow.createStepId();

                    workflow.createStep(null, stepDescription, waitForExport, systemSource.getId(), systemSource.getSystemType(),
                            getClass(), replicateNFSExportRulesMethod, null, replicateNFSExportRulesStep);
                }
                // Replicate CIFS shares and ACLs from Target File System to Source.

                SMBShareMap smbShareMap = targetFileShare.getSMBFileShares();

                if (smbShareMap != null && cifsPort != null) {

                    stepDescription = "Replicating Target File System CIFS Shares To Source Cluster";
                    Workflow.Method replicateCIFSShareMethod = new Workflow.Method(REPLICATE_FILESYSTEM_CIFS_SHARES_METHOD,
                            systemSource.getId(), targetFileShare.getId(), cifsPort);
                    String replicateCIFSShareStep = workflow.createStepId();

                    String waitForShare = workflow.createStep(null, stepDescription, waitForFailback, systemSource.getId(),
                            systemSource.getSystemType(), getClass(), replicateCIFSShareMethod, null,
                            replicateCIFSShareStep);

                    stepDescription = "Replicating Target File System CIFS Share ACLs To Source Cluster";
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

            workflow = this._workflowService.getNewWorkflow(this, FAILOVER_FILESYSTEMS_WF_NAME, false, taskId);

            // Failover File System to Target
            s_logger.info("Generating steps for Failover File System to Target");
            String failoverStep = workflow.createStepId();
            MirrorFileFailoverTaskCompleter failoverCompleter = new MirrorFileFailoverTaskCompleter(sourceFileShare.getId(),
                    targetFileShare.getId(), failoverStep);
            stepDescription = String.format("Failover Source File System %s to Target System.", sourceFileShare.getLabel());
            Object[] args = new Object[] { systemTarget.getId(), targetFileShare.getId(), failoverCompleter };
            String waitForFailover = _fileReplicationDeviceController.createMethod(workflow, null, null,
                    FAILOVER_FILE_SYSTEM_METHOD, failoverStep, stepDescription, systemTarget.getId(), args);

            if (replicateConfiguration) {
                // Replicate CIFS shares and ACLs to Target Cluster.
                SMBShareMap smbShareMap = sourceFileShare.getSMBFileShares();

                if (smbShareMap != null && cifsPort != null) {

                    stepDescription = "Replicating Source File System CIFS Shares To Target Cluster";
                    Workflow.Method replicateCIFSShareMethod = new Workflow.Method(REPLICATE_FILESYSTEM_CIFS_SHARES_METHOD,
                            systemTarget.getId(), fsURI, cifsPort);
                    String replicateCIFSShareStep = workflow.createStepId();
                    String waitForShare = workflow.createStep(null, stepDescription, waitForFailover, systemTarget.getId(),
                            systemTarget.getSystemType(), getClass(), replicateCIFSShareMethod, null, replicateCIFSShareStep);

                    stepDescription = "Replicating Source File System CIFS Share ACls To Target Cluster";
                    Workflow.Method replicateCIFSShareACLsMethod = new Workflow.Method(REPLICATE_FILESYSTEM_CIFS_SHARE_ACLS_METHOD,
                            systemTarget.getId(), fsURI);
                    String replicateCIFSShareACLsStep = workflow.createStepId();
                    workflow.createStep(null, stepDescription, waitForShare, systemTarget.getId(),
                            systemTarget.getSystemType(), getClass(), replicateCIFSShareACLsMethod, null, replicateCIFSShareACLsStep);
                }

                // Replicate NFS export and rules to Target Cluster.
                FSExportMap nfsExportMap = sourceFileShare.getFsExports();

                if (nfsExportMap != null && nfsPort != null) {

                    stepDescription = "Replicating Source File System NFS Exports To Target Cluster";
                    Workflow.Method replicateNFSExportMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_EXPORT_METHOD,
                            systemTarget.getId(), fsURI, nfsPort);
                    String replicateNFSExportStep = workflow.createStepId();
                    String waitForExport = workflow.createStep(null, stepDescription, waitForFailover, systemTarget.getId(),
                            systemTarget.getSystemType(), getClass(), replicateNFSExportMethod, null, replicateNFSExportStep);

                    stepDescription = "Replicating Source File System NFS Export Rules To Target Cluster";
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
     * @param systemTarget
     *            - URI of target StorageSystem where source CIFS shares has to be replicated.
     * @param fsURI
     *            -URI of the source FileSystem
     * @param cifsPort
     *            -StoragePort, CIFS port of target File System where new shares has to be created.
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

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_CIFS_SHARES_TO_TARGET_WF_NAME, false, taskId);

            SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();
            List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());

            SMBShareMap targetSMBShareMap = targetFileShare.getSMBFileShares();

            if (targetSMBShareMap == null) {
                createCIFSShareOnTarget(workflow, systemTarget, sourceSMBShares, cifsPort, targetFileShare, sourceFileShare);
            } else {
                List<SMBFileShare> targetSMBShares = new ArrayList<SMBFileShare>(targetSMBShareMap.values());

                List<SMBFileShare> targetSMBSharestoDelete = new ArrayList<SMBFileShare>();
                List<SMBFileShare> targetSMBSharestoCreate = new ArrayList<SMBFileShare>();

                List<String> sourceSMBSharesNameList = new ArrayList<String>();
                List<String> targetSMBSharesNameList = new ArrayList<String>();

                for (SMBFileShare sourceSMBShare : sourceSMBShares) {
                    sourceSMBSharesNameList.add(sourceSMBShare.getName());
                }
                for (SMBFileShare targetSMBShare : targetSMBShares) {
                    targetSMBSharesNameList.add(targetSMBShare.getName());
                }

                for (SMBFileShare sourceSMBShare : sourceSMBShares) {
                    if (!targetSMBSharesNameList.contains(sourceSMBShare.getName())) {
                        targetSMBSharestoCreate.add(sourceSMBShare);
                    }
                }
                for (SMBFileShare targetSMBShare : targetSMBShares) {
                    if (!sourceSMBSharesNameList.contains(targetSMBShare.getName())) {
                        targetSMBSharestoDelete.add(targetSMBShare);
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
                    "Replicating source File System : %s CIFS Shares to Target System finished successfully",
                    sourceFileShare.getLabel());

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
     * @param systemTarget
     *            - URI of target StorageSystem where source CIFS shares has to be replicated.
     * @param fsURI
     *            -URI of the source FileSystem
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

            SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();
            List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_CIFS_SHARE_ACLS_TO_TARGET_WF_NAME, false, taskId);

            for (SMBFileShare sourceSMBShare : sourceSMBShares) {
                List<ShareACL> sourceShareACLs = queryShareACLs(sourceSMBShare.getName(), sourceFileShare.getId());
                List<ShareACL> targetShareACLs = queryShareACLs(sourceSMBShare.getName(), targetFileShare.getId());

                if (sourceShareACLs != null && !sourceShareACLs.isEmpty()) {
                    if (targetShareACLs.isEmpty()) {
                        params = new CifsShareACLUpdateParams();
                        ShareACLs shareACLs = new ShareACLs();
                        shareACLs.setShareACLs(sourceShareACLs);
                        params.setAclsToAdd(shareACLs);

                        String stepDescription = String.format("Replicating Source File System CIFS Share : %s ACLs : %s On Target Cluster",
                                sourceSMBShare.getName(), params.toString());
                        String shareACLUpdateStep = workflow.createStepId();
                        Object[] args = new Object[] { systemTarget, targetFileShare.getId(), sourceSMBShare.getName(), params };
                        _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_SHARE_ACLS_METHOD, shareACLUpdateStep,
                                stepDescription, systemTarget, args);
                    } else {

                        List<ShareACL> shareACLsToAdd = new ArrayList<ShareACL>();
                        List<ShareACL> shareACLsToDelete = new ArrayList<ShareACL>();
                        List<ShareACL> shareACLsToModify = new ArrayList<ShareACL>();

                        List<String> sourceShareACLsNameList = new ArrayList<String>();
                        List<String> targetShareACLsNameList = new ArrayList<String>();

                        for (ShareACL sourceShareACL : sourceShareACLs) {
                            if (sourceShareACL.getUser() != null && !sourceShareACL.getUser().isEmpty()) {
                                sourceShareACLsNameList.add(sourceShareACL.getUser());
                            } else {
                                sourceShareACLsNameList.add(sourceShareACL.getGroup());
                            }
                        }

                        for (ShareACL targetShareACL : targetShareACLs) {
                            if (targetShareACL.getUser() != null && !targetShareACL.getUser().isEmpty()) {
                                targetShareACLsNameList.add(targetShareACL.getUser());
                            } else {
                                targetShareACLsNameList.add(targetShareACL.getGroup());
                            }
                        }

                        // ACLs To Add
                        for (ShareACL sourceShareACL : sourceShareACLs) {
                            if (sourceShareACL.getUser() != null && !sourceShareACL.getUser().isEmpty()
                                    && !targetShareACLsNameList.contains(sourceShareACL.getUser())) {
                                ShareACL shareACL = sourceShareACL;
                                shareACL.setFileSystemId(targetFileShare.getId());
                                shareACLsToAdd.add(shareACL);

                            } else if (sourceShareACL.getGroup() != null && !sourceShareACL.getGroup().isEmpty()
                                    && !targetShareACLsNameList.contains(sourceShareACL.getGroup())) {
                                ShareACL shareACL = sourceShareACL;
                                shareACL.setFileSystemId(targetFileShare.getId());
                                shareACLsToAdd.add(shareACL);
                            }
                        }

                        // ACLs To Delete
                        for (ShareACL targetShareACL : targetShareACLs) {
                            if (targetShareACL.getUser() != null && !targetShareACL.getUser().isEmpty()
                                    && !sourceShareACLsNameList.contains(targetShareACL.getUser())) {
                                shareACLsToDelete.add(targetShareACL);

                            } else if (targetShareACL.getGroup() != null && !targetShareACL.getGroup().isEmpty()
                                    && !sourceShareACLsNameList.contains(targetShareACL.getGroup())) {
                                shareACLsToDelete.add(targetShareACL);
                            }
                        }

                        // ACLs to Modify
                        targetShareACLs.removeAll(shareACLsToDelete);
                        sourceShareACLs.removeAll(shareACLsToAdd);
                        for (ShareACL sourceShareACL : sourceShareACLs) {
                            for (ShareACL targetShareACL : targetShareACLs) {

                                if (targetShareACL.getUser() != null && !targetShareACL.getUser().isEmpty()
                                        && targetShareACL.getUser().equals(sourceShareACL.getUser())
                                        && !targetShareACL.getPermission().equals(sourceShareACL.getPermission())) {
                                    ShareACL shareACL = targetShareACL;
                                    shareACL.setPermission(sourceShareACL.getPermission());
                                    shareACLsToModify.add(shareACL);

                                } else if ((targetShareACL.getGroup() != null && !targetShareACL.getGroup().isEmpty())
                                        && targetShareACL.getGroup().equals(sourceShareACL.getGroup())
                                        && !targetShareACL.getPermission().equals(sourceShareACL.getPermission())) {
                                    ShareACL shareACL = targetShareACL;
                                    shareACL.setPermission(sourceShareACL.getPermission());
                                    shareACLsToModify.add(shareACL);
                                }
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

                        String stepDescription = String.format(
                                "Replicating Source File System CIFS Share ACLs On Target Cluster, CIFS Share : %s, ACLs details: %s",
                                sourceSMBShare.getName(), params.toString());
                        String shareACLUpdateStep = workflow.createStepId();
                        Object[] args = new Object[] { systemTarget, targetFileShare.getId(), sourceSMBShare.getName(), params };
                        _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_SHARE_ACLS_METHOD, shareACLUpdateStep,
                                stepDescription, systemTarget, args);
                    }
                }
            }
            String successMessage = String.format(
                    "Replicating source File System : %s CIFS Shares ACLs to Target System finished successfully",
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
     * @param systemTarget
     *            - URI of target StorageSystem where source NFS shares has to be replicated.
     * @param fsURI
     *            -URI of the source FileSystem
     * @param nfsPort
     *            -StoragePort, NFS port of target File System where new export has to be created.
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

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_NFS_EXPORT_TO_TARGET_WF_NAME, false, taskId);

            FSExportMap sourceNFSExportMap = sourceFileShare.getFsExports();
            List<FileExport> sourceNFSExports = new ArrayList<FileExport>(sourceNFSExportMap.values());
            FSExportMap targetNFSExportMap = targetFileShare.getFsExports();

            if (targetNFSExportMap == null) {
                createNFSExportOnTarget(workflow, systemTarget, sourceNFSExports, nfsPort, targetFileShare, sourceFileShare);

            } else {

                List<FileExport> targetNFSExports = new ArrayList<FileExport>(targetNFSExportMap.values());
                List<FileExport> targetNFSExportstoCreate = new ArrayList<FileExport>();
                List<String> targetNFSExportPaths = new ArrayList<String>();
                List<String> sourceNFSExportPaths = new ArrayList<String>();

                for (FileExport targetNFSExport : targetNFSExports) {
                    targetNFSExportPaths.add(targetNFSExport.getPath());
                }

                for (FileExport sourceNFSExport : sourceNFSExports) {
                    sourceNFSExportPaths.add(sourceNFSExport.getPath());
                }

                for (String sourceNFSExportPath : sourceNFSExportPaths) {

                    if (sourceNFSExportPath.equals(sourceFileShare.getPath())
                            && !targetNFSExportPaths.contains(targetFileShare.getPath())) {
                        for (FileExport sourceNFSExport : sourceNFSExports) {
                            if (sourceNFSExport.getPath().equals(sourceNFSExportPath)) {
                                targetNFSExportstoCreate.add(sourceNFSExport);
                            }
                        }

                    } else if (!sourceNFSExportPath.equals(sourceFileShare.getPath())) {
                        ArrayList<String> subdirName = new ArrayList<String>();
                        subdirName.add(sourceNFSExportPath.split(sourceFileShare.getPath())[1]);
                        if (!targetNFSExportPaths.contains(targetFileShare.getPath() + subdirName.get(0))) {
                            for (FileExport sourceNFSExport : sourceNFSExports) {
                                if (sourceNFSExport.getPath().equals(sourceNFSExportPath)) {
                                    targetNFSExportstoCreate.add(sourceNFSExport);
                                }
                            }
                        }
                    }
                }

                if (!targetNFSExportstoCreate.isEmpty()) {
                    createNFSExportOnTarget(workflow, systemTarget, targetNFSExportstoCreate, nfsPort, targetFileShare, sourceFileShare);
                }
            }
            String successMessage = String.format(
                    "Replicating source File System : %s NFS Exports to Target System finished successfully", sourceFileShare.getLabel());
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
     * @param systemTarget
     *            - URI of target StorageSystem where source NFS shares has to be replicated.
     * @param fsURI
     *            -URI of the source FileSystem
     * @param taskId
     */
    public void addStepsToReplicateNFSExportRules(URI systemTarget, URI fsURI, String taskId) {
        s_logger.info("Generating steps for Replicating NFS export rules to Target Cluster");
        FileWorkflowCompleter completer = new FileWorkflowCompleter(fsURI, taskId);
        Workflow workflow = null;
        List<ExportRule> exportRulesToAdd = new ArrayList<ExportRule>();
        List<ExportRule> exportRulesToModify = new ArrayList<ExportRule>();
        List<ExportRule> exportRulesToDelete = new ArrayList<ExportRule>();
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

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_NFS_EXPORT_RULES_TO_TARGET_WF_NAME, false, taskId);

            List<ExportRule> sourceExportRules = queryFSExportRules(sourceFileShare);
            List<ExportRule> targetExportRules = queryFSExportRules(targetFileShare);

            // Export Rule To Add
            for (ExportRule sourceExportRule : sourceExportRules) {
                boolean isSecFlvPresentOnTarget = false;
                if (sourceExportRule.getExportPath().equals(sourceFileShare.getPath())) {
                    // Not the sub directory Export Rule
                    for (ExportRule targetExportRule : targetExportRules) {
                        if (targetExportRule.getExportPath().equals(targetFileShare.getPath()) &&
                                targetExportRule.getSecFlavor().equals(sourceExportRule.getSecFlavor())) {
                            isSecFlvPresentOnTarget = true;
                        }
                    }
                    if (!isSecFlvPresentOnTarget) {
                        ExportRule exportRule = new ExportRule();
                        exportRule.setFsID(targetFileShare.getId());
                        exportRule.setExportPath(targetFileShare.getPath());
                        exportRule.setAnon(sourceExportRule.getAnon());
                        exportRule.setReadOnlyHosts(sourceExportRule.getReadOnlyHosts());
                        exportRule.setRootHosts(sourceExportRule.getRootHosts());
                        exportRule.setReadWriteHosts(sourceExportRule.getReadWriteHosts());
                        exportRule.setSecFlavor(sourceExportRule.getSecFlavor());
                        exportRulesToAdd.add(exportRule);
                    }

                } else {
                    // Sub directory Export Rule
                    for (ExportRule targetExportRule : targetExportRules) {
                        if (!targetExportRule.getExportPath().equals(targetFileShare.getPath()) &&
                                targetExportRule.getSecFlavor().equals(sourceExportRule.getSecFlavor())) {
                            isSecFlvPresentOnTarget = true;
                        }
                    }
                    if (!isSecFlvPresentOnTarget) {
                        ExportRule exportRule = new ExportRule();
                        exportRule.setFsID(targetFileShare.getId());
                        ArrayList<String> subdirName = new ArrayList<String>();
                        subdirName.add(sourceExportRule.getExportPath().split(sourceFileShare.getPath())[1]);
                        exportRule.setExportPath(targetFileShare.getPath() + subdirName.get(0));
                        exportRule.setAnon(sourceExportRule.getAnon());
                        exportRule.setReadOnlyHosts(sourceExportRule.getReadOnlyHosts());
                        exportRule.setRootHosts(sourceExportRule.getRootHosts());
                        exportRule.setReadWriteHosts(sourceExportRule.getReadWriteHosts());
                        exportRule.setSecFlavor(sourceExportRule.getSecFlavor());
                        exportRulesToAdd.add(exportRule);
                    }
                }
            }

            // Export Rule To Delete
            for (ExportRule targetExportRule : targetExportRules) {
                boolean isSecFlvPresentOnSource = false;
                if (targetExportRule.getExportPath().equals(targetFileShare.getPath())) {
                    // Not the sub directory Export Rule
                    for (ExportRule sourceExportRule : sourceExportRules) {
                        if (sourceExportRule.getExportPath().equals(sourceFileShare.getPath()) &&
                                sourceExportRule.getSecFlavor().equals(targetExportRule.getSecFlavor())) {
                            isSecFlvPresentOnSource = true;
                        }
                    }
                    if (!isSecFlvPresentOnSource) {
                        exportRulesToDelete.add(targetExportRule);
                    }

                } else {
                    // Sub directory Export Rule
                    for (ExportRule sourceExportRule : sourceExportRules) {
                        if (!sourceExportRule.getExportPath().equals(sourceFileShare.getPath()) &&
                                sourceExportRule.getSecFlavor().equals(targetExportRule.getSecFlavor())) {
                            isSecFlvPresentOnSource = true;
                        }
                    }
                    if (!isSecFlvPresentOnSource) {
                        exportRulesToDelete.add(targetExportRule);
                    }
                }
            }

            // Export Rule To Modify
            sourceExportRules.removeAll(exportRulesToAdd);
            targetExportRules.removeAll(exportRulesToDelete);

            for (ExportRule sourceExportRule : sourceExportRules) {
                if (sourceExportRule.getExportPath().equals(sourceFileShare.getPath())) {
                    // Not the sub directory Export Rule
                    for (ExportRule targetExportRule : targetExportRules) {
                        if (targetExportRule.getExportPath().equals(targetFileShare.getPath()) &&
                                targetExportRule.getSecFlavor().equals(sourceExportRule.getSecFlavor())) {
                            checkForExportRuleToModify(sourceExportRule, targetExportRule, exportRulesToModify);
                        }
                    }
                } else {
                    // Sub directory Export Rule
                    for (ExportRule targetExportRule : targetExportRules) {
                        if (!targetExportRule.getExportPath().equals(targetFileShare.getPath()) &&
                                targetExportRule.getSecFlavor().equals(sourceExportRule.getSecFlavor())) {
                            checkForExportRuleToModify(sourceExportRule, targetExportRule, exportRulesToModify);
                        }
                    }
                }
            }

            FileExportUpdateParams params = new FileExportUpdateParams();

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

            String stepDescription = String.format("Replicating Source File System Export Rules On Target Cluster : %s", params.toString());
            String exportRuleUpdateStep = workflow.createStepId();
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), params };
            _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_EXPORT_RULES_METHOD, exportRuleUpdateStep,
                    stepDescription, systemTarget, args);

            String successMessage = String.format(
                    "Replicating source File System : %s NFS Exports Rules to Target System finished successfully",
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
            String stepDescription = String.format("Replicating Source File System NFS Export : %s To Target Cluster",
                    nfsExport.getMountPath());
            String exportCreationStep = workflow.createStepId();
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), Arrays.asList(fileNFSExport) };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_EXPORT_METHOD, exportCreationStep, stepDescription,
                    systemTarget, args);
        }
    }

    private static List<ShareACL> queryShareACLs(String shareName, URI fs) {

        List<ShareACL> aclList = new ArrayList<ShareACL>();
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileCifsShareAclsConstraint(fs);
        List<CifsShareACL> shareAclList = CustomQueryUtility.queryActiveResourcesByConstraint(s_dbClient, CifsShareACL.class,
                containmentConstraint);

        if (shareAclList != null) {
            Iterator<CifsShareACL> shareAclIter = shareAclList.iterator();
            while (shareAclIter.hasNext()) {

                CifsShareACL dbShareAcl = shareAclIter.next();
                if (shareName.equals(dbShareAcl.getShareName())) {
                    ShareACL acl = new ShareACL();
                    acl.setShareName(shareName);
                    acl.setDomain(dbShareAcl.getDomain());
                    acl.setUser(dbShareAcl.getUser());
                    acl.setGroup(dbShareAcl.getGroup());
                    acl.setPermission(dbShareAcl.getPermission());
                    acl.setFileSystemId(fs);
                    aclList.add(acl);
                }
            }
        }
        return aclList;
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
            String stepDescription = String.format("Creating Source File System CIFS Share : %s on Target System.",
                    fileSMBShare.getName());
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), fileSMBShare };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_SHARE_METHOD, shareCreationStep, stepDescription,
                    systemTarget, args);
        }
    }

    private static void deleteCIFSShareFromTarget(Workflow workflow, URI systemTarget, List<SMBFileShare> smbShares,
            FileShare targetFileShare) {
        for (SMBFileShare smbShare : smbShares) {
            FileSMBShare fileSMBShare = new FileSMBShare(smbShare);
            String stepDescription = "Deleting Target File System CIFS Share: " + fileSMBShare.getName();
            String sharedeleteStep = workflow.createStepId();
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), fileSMBShare };
            _fileDeviceController.createMethod(workflow, null, DELETE_FILESYSTEM_SHARE_METHOD, sharedeleteStep,
                    stepDescription, systemTarget, args);
        }
    }

    private static List<ExportRule> queryFSExportRules(FileShare fs) {
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
        List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(s_dbClient, FileExportRule.class,
                containmentConstraint);
        List<ExportRule> exportRules = new ArrayList<ExportRule>();

        for (FileExportRule fileExportRule : fileExportRules) {
            ExportRule exportRule = new ExportRule();
            exportRule.setAnon(fileExportRule.getAnon());
            exportRule.setExportPath(fileExportRule.getExportPath());
            exportRule.setFsID(fileExportRule.getFileSystemId());
            exportRule.setMountPoint(fileExportRule.getMountPoint());
            exportRule.setReadOnlyHosts(fileExportRule.getReadOnlyHosts());
            exportRule.setReadWriteHosts(fileExportRule.getReadWriteHosts());
            exportRule.setRootHosts(fileExportRule.getRootHosts());
            exportRule.setSecFlavor(fileExportRule.getSecFlavor());
            exportRule.setSnapShotID(fileExportRule.getSnapshotId());
            exportRule.setDeviceExportId(fileExportRule.getDeviceExportId());
            exportRules.add(exportRule);
        }
        return exportRules;
    }

    private static void checkForExportRuleToModify(ExportRule sourceExportRule, ExportRule targetExportRule,
            List<ExportRule> exportRulesToModify) {
        boolean isExportRuleToModify = false;

        if (sourceExportRule.getReadWriteHosts() == null && targetExportRule.getReadWriteHosts() == null) {
            // Both Source and Target export rule don't have any read-write host...
        } else if ((sourceExportRule.getReadWriteHosts() == null && targetExportRule.getReadWriteHosts() != null)
                || (!sourceExportRule.getReadWriteHosts().equals(targetExportRule.getReadWriteHosts()))) {
            isExportRuleToModify = true;
            targetExportRule.setReadWriteHosts(sourceExportRule.getReadWriteHosts());
        }

        if (sourceExportRule.getReadOnlyHosts() == null && targetExportRule.getReadOnlyHosts() == null) {
            // Both Source and Target export rule don't have any read-only host...
        } else if ((sourceExportRule.getReadOnlyHosts() == null && targetExportRule.getReadOnlyHosts() != null)
                || (!sourceExportRule.getReadOnlyHosts().equals(targetExportRule.getReadOnlyHosts()))) {
            isExportRuleToModify = true;
            targetExportRule.setReadOnlyHosts(sourceExportRule.getReadOnlyHosts());
        }

        if (sourceExportRule.getRootHosts() == null && targetExportRule.getRootHosts() == null) {
            // Both Source and Target export rule don't have any root host...
        } else if ((sourceExportRule.getRootHosts() == null && targetExportRule.getRootHosts() != null)
                || (!sourceExportRule.getRootHosts().equals(targetExportRule.getRootHosts()))) {
            isExportRuleToModify = true;
            targetExportRule.setRootHosts(sourceExportRule.getRootHosts());
        }

        if (sourceExportRule.getAnon() != null && !sourceExportRule.getAnon().equals(targetExportRule.getAnon())) {
            isExportRuleToModify = true;
            targetExportRule.setAnon(sourceExportRule.getAnon());
        }
        if (isExportRuleToModify) {
            exportRulesToModify.add(targetExportRule);
        }
    }
}
