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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.PolicyStorageResource;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.fileorchestrationcontroller.FileStorageSystemAssociation.TargetAssociation;
import com.emc.storageos.filereplicationcontroller.FileReplicationDeviceController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.FileNfsACLUpdateParams;
import com.emc.storageos.model.file.MountInfo;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.model.file.policy.FilePolicyUpdateParam;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.file.CreateMirrorFileSystemsCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileCreateWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileDeleteWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FilePolicyAssignWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FilePolicyUnAssignWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileProtectionPolicyUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileSnapshotWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileSystemAssignPolicyWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileFailbackTaskCompleter;
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
    private CustomConfigHandler customConfigHandler;

    static final String CREATE_FILESYSTEMS_WF_NAME = "CREATE_FILESYSTEMS_WORKFLOW";
    static final String DELETE_FILESYSTEMS_WF_NAME = "DELETE_FILESYSTEMS_WORKFLOW";
    static final String EXPAND_FILESYSTEMS_WF_NAME = "EXPAND_FILESYSTEMS_WORKFLOW";
    static final String REDUCE_FILESYSTEMS_WF_NAME = "REDUCE_FILESYSTEMS_WORKFLOW";
    static final String CHANGE_FILESYSTEMS_VPOOL_WF_NAME = "CHANGE_FILESYSTEMS_VPOOL_WORKFLOW";
    static final String CREATE_MIRROR_FILESYSTEMS_WF_NAME = "CREATE_MIRROR_FILESYSTEMS_WORKFLOW";
    static final String CREATE_FILESYSTEM_CIFS_SHARE_WF_NAME = "CREATE_FILESYSTEM_CIFS_SHARE_WORKFLOW";
    static final String CREATE_FILESYSTEM_NFS_EXPORT_WF_NAME = "CREATE_FILESYSTEM_NFS_EXPORT_WORKFLOW";
    static final String UPDATE_FILESYSTEM_EXPORT_RULES_WF_NAME = "UPDATE_FILESYSTEM_EXPORT_RULES_WORKFLOW";
    static final String UPDATE_FILESYSTEM_CIFS_SHARE_ACLS_WF_NAME = "UPDATE_FILESYSTEM_CIFS_SHARE_ACLS_WORKFLOW";
    static final String CREATE_FILESYSTEM_SNAPSHOT_WF_NAME = "CREATE_FILESYSTEM_SNAPSHOT_WORKFLOW";
    static final String DELETE_FILESYSTEM_CIFS_SHARE_WF_NAME = "DELETE_FILESYSTEM_CIFS_SHARE_WORKFLOW";
    static final String DELETE_FILESYSTEM_EXPORT_RULES_WF_NAME = "DELETE_FILESYSTEM_EXPORT_RULES_WORKFLOW";
    static final String EXPAND_FILESYSTEM_WF_NAME = "EXPAND_FILESYSTEM_WORFLOW";
    static final String DELETE_FILESYSTEM_SNAPSHOT_WF_NAME = "DELETE_FILESYSTEM_SNAPSHOT_WORKFLOW";
    static final String RESTORE_FILESYSTEM_SNAPSHOT_WF_NAME = "RESTORE_FILESYSTEM_SNAPSHOT_WORFLOW";
    static final String DELETE_FILESYSTEM_SHARE_ACLS_WF_NAME = "DELETE_FILESYSTEM_SHARE_ACLS_WORKFLOW";
    static final String REPLICATE_QUOTA_DIR_SETTINGS_TO_TARGET_WF_NAME = "REPLICATE_QUOTA_DIR_SETTINGS_TO_TARGET_WORKFLOW";
    static final String UNASSIGN_FILE_POLICY_WF_NAME = "UNASSIGN_FILE_POLICY_WORKFLOW";
    static final String ASSIGN_FILE_POLICY_WF_NAME = "ASSIGN_FILE_POLICY_WORKFLOW";
    static final String UPDATE_FILE_POLICY_WF_NAME = "UPDATE_FILE_POLICY_WORKFLOW";
    static final String ASSIGN_FILE_POLICY_TO_FS_WF_NAME = "ASSIGN_FILE_POLICY_TO_FILE_SYSTEM_WORKFLOW";

    static final String FAILOVER_FILESYSTEMS_WF_NAME = "FAILOVER_FILESYSTEM_WORKFLOW";
    static final String FAILBACK_FILESYSTEMS_WF_NAME = "FAILBACK_FILESYSTEM_WORKFLOW";
    static final String REPLICATE_CIFS_SHARES_TO_TARGET_WF_NAME = "REPLICATE_CIFS_SHARES_TO_TARGET_WORKFLOW";
    static final String REPLICATE_CIFS_SHARE_ACLS_TO_TARGET_WF_NAME = "REPLICATE_CIFS_SHARE_ACLS_TO_TARGET_WORKFLOW";
    static final String REPLICATE_NFS_EXPORT_TO_TARGET_WF_NAME = "REPLICATE_NFS_EXPORT_TO_TARGET_WORFLOW";
    static final String REPLICATE_NFS_EXPORT_RULES_TO_TARGET_WF_NAME = "REPLICATE_NFS_EXPORT_RULES_TO_TARGET_WORFLOW";
    static final String REPLICATE_NFS_ACLS_TO_TARGET_WF_NAME = "REPLICATE_NFS_ACLS_TO_TARGET_WORKFLOW";

    private static final String CREATE_FILESYSTEM_EXPORT_METHOD = "export";
    private static final String CREATE_FILESYSTEM_SHARE_METHOD = "share";
    private static final String UPDATE_FILESYSTEM_SHARE_ACLS_METHOD = "updateShareACLs";
    private static final String UPDATE_FILESYSTEM_NFS_ACL_METHOD = "updateNFSAcl";
    private static final String UPDATE_FILESYSTEM_EXPORT_RULES_METHOD = "updateExportRules";
    private static final String CREATE_FILESYSTEM_SNAPSHOT_METHOD = "snapshotFS";
    private static final String DELETE_FILESYSTEM_SHARE_METHOD = "deleteShare";
    private static final String DELETE_FILESYSTEM_EXPORT_RULES = "deleteExportRules";
    private static final String MODIFY_FILESYSTEM_METHOD = "modifyFS";
    private static final String FAILOVER_FILE_SYSTEM_METHOD = "failoverFileSystem";
    private static final String FAILBACK_FILE_SYSTEM_METHOD = "doFailBackMirrorSessionWF";
    private static final String FILE_REPLICATION_OPERATIONS_METHOD = "performFileReplicationOperation";
    private static final String REPLICATE_FILESYSTEM_DIRECTORY_QUOTA_SETTINGS_METHOD = "addStepsToReplicateDirectoryQuotaSettings";
    private static final String REPLICATE_FILESYSTEM_CIFS_SHARES_METHOD = "addStepsToReplicateCIFSShares";
    private static final String REPLICATE_FILESYSTEM_CIFS_SHARE_ACLS_METHOD = "addStepsToReplicateCIFSShareACLs";
    private static final String REPLICATE_FILESYSTEM_NFS_EXPORT_METHOD = "addStepsToReplicateNFSExports";
    private static final String REPLICATE_FILESYSTEM_NFS_EXPORT_RULE_METHOD = "addStepsToReplicateNFSExportRules";
    private static final String REPLICATE_FILESYSTEM_NFS_ACLS_METHOD = "addStepsToReplicateNFSACLs";
    private static final String RESTORE_FILESYSTEM_SNAPSHOT_METHOD = "restoreFS";
    private static final String DELETE_FILESYSTEM_SNAPSHOT_METHOD = "delete";
    private static final String DELETE_FILESYSTEM_SHARE_ACLS_METHOD = "deleteShareACLs";
    private static final String DELETE_FILESYSTEM_EXPORT_METHOD = "deleteExportRules";

    private static final String UNMOUNT_FILESYSTEM_EXPORT_METHOD = "unmountDevice";
    private static final String VERIFY_MOUNT_DEPENDENCIES_METHOD = "verifyMountDependencies";
    private static final String CHECK_IF_EXPORT_IS_MOUNTED = "CheckIfExportIsMounted";

    private static final String APPLY_FILE_POLICY_METHOD = "applyFilePolicy";
    private static final String UNASSIGN_FILE_POLICY_METHOD = "unassignFilePolicy";
    private static final String ASSIGN_FILE_SNAPSHOT_POLICY_TO_VIRTUAL_POOLS_METHOD = "assignFileSnapshotPolicyToVirtualPools";
    private static final String ASSIGN_FILE_SNAPSHOT_POLICY_TO_PROJECTS_METHOD = "assignFileSnapshotPolicyToProjects";
    private static final String UPDATE_STORAGE_SYSTEM_FILE_PROTECTION_POLICY_METHOD = "updateStorageSystemFileProtectionPolicy";
    private static final String ASSIGN_FILE_REPLICATION_POLICY_TO_VIRTUAL_POOLS_METHOD = "assignFileReplicationPolicyToVirtualPools";
    private static final String ASSIGN_FILE_REPLICATION_POLICY_TO_PROJECTS_METHOD = "assignFileReplicationPolicyToProjects";
    private static final String CHECK_FILE_POLICY_PATH_HAS_RESOURCE_LABEL_METHOD = "checkFilePolicyPathHasResourceLabel";

    public void setCustomConfigHandler(CustomConfigHandler customConfigHandler) {
        this.customConfigHandler = customConfigHandler;
    }
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

            // second, check for policies that has to applied on this file system..
            waitFor = addStepsForApplyingPolicies(workflow, waitFor, fileDescriptors);

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
     * @param fs
     * @param fileDescriptors
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
        List<URI> fileShareUris = FileDescriptor.getFileSystemURIs(fileDescriptors);
        FileDeleteWorkflowCompleter completer = new FileDeleteWorkflowCompleter(fileShareUris, taskId);
        Workflow workflow = null;

        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this,
                    DELETE_FILESYSTEMS_WF_NAME, false, taskId);

            // call the FileDeviceController to add its delete methods.
            _fileDeviceController.addStepsForDeleteFileSystems(workflow, null, fileDescriptors, taskId);

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
            stepDescription = String.format("Creating CIFS share for file system : %s, share name: %s ", uri, smbShare.getName());
            successMessage = String.format("Creating CIFS share for file system : %s, share name: %s finished succesfully.", uri,
                    smbShare.getName());
            opName = ResourceOperationTypeEnum.CREATE_FILE_SYSTEM_SHARE.getName();
        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, taskId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = String.format("Creating CIFS share for file system snapshot : %s, share name: %s ", uri, smbShare.getName());
            successMessage = String.format("Creating CIFS share for file system : %s, share name: %s finished succesfully.", uri,
                    smbShare.getName());
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
            s_logger.error(String.format("Creating CIFS share for file system : %s, share name: %s failed.", uri,
                    smbShare.getName()), ex);
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
            stepDescription = String.format("Creating NFS export for file system : %s", uri);
            successMessage = String.format("Creating NFS export for file system : %s finished succesfully.", uri);
            opName = ResourceOperationTypeEnum.EXPORT_FILE_SYSTEM.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = String.format("Creating NFS export for file system snapshot : %s", uri);
            successMessage = String.format("Creating NFS export for file system snapshot : %s finished succesfully.", uri);
            opName = ResourceOperationTypeEnum.EXPORT_FILE_SNAPSHOT.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, CREATE_FILESYSTEM_NFS_EXPORT_WF_NAME, false, opId, completer);
            String exportStep = workflow.createStepId();
            Object[] args = new Object[] { storage, uri, exports };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_EXPORT_METHOD, exportStep, stepDescription, storage, args);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error(String.format("Creating NFS export for file system/snapshot : %s failed", uri), ex);
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
            stepDescription = String.format("Updating file system : %s export rules: %s", uri, param.toString());
            successMessage = String.format("Updating file system : %s export rules: %s finished successfully.", uri, param.toString());
            opName = ResourceOperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SYSTEM.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = String.format("Updating file system : %s export rules: %s", uri, param.toString());
            successMessage = String.format("Updating file system : %s export rules: %s finished successfully.", uri, param.toString());
            opName = ResourceOperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SNAPSHOT.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_FILESYSTEM_EXPORT_RULES_WF_NAME, false, opId, completer);
            String waitFor = null;
            // Check if the export should be unmounted before deleting
            if (unmountExport) {
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
            s_logger.error(String.format("Updating file system : %s export rules: %s failed.", uri, param.toString()), ex);
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
            stepDescription = String.format("Updating file system : %s share : %s  ACLs: %s", uri, shareName, param.toString());
            successMessage = String.format("Updating file system : %s share : %s  ACLs: %s finished successfully.", uri, shareName,
                    param.toString());
            opName = ResourceOperationTypeEnum.UPDATE_FILE_SYSTEM_SHARE_ACL.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = String.format("Updating file system snapshot : %s share : %s  ACLs: %s", uri, shareName, param.toString());
            successMessage = String.format("Updating file system snapshot : %s share : %s  ACLs: %s finished successfully.", uri,
                    shareName,
                    param.toString());
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
            s_logger.error(String.format("Updating file system : %s share : %s  ACLs: %s failed.", uri, shareName, param.toString()), ex);
            ServiceError serviceError = DeviceControllerException.errors.updateFileShareCIFSACLsFailed(fileObj.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void snapshotFS(URI storage, URI snapshot, URI fsURI, String opId) throws ControllerException {
        // Using VNXeFSSnapshotTaskCompleter as it will serve the purpose..
        VNXeFSSnapshotTaskCompleter completer = new VNXeFSSnapshotTaskCompleter(Snapshot.class, snapshot, opId);
        Workflow workflow = null;
        try {
            workflow = _workflowService.getNewWorkflow(this, CREATE_FILESYSTEM_SNAPSHOT_WF_NAME, false, opId, completer);
            String snapshotFSStep = workflow.createStepId();
            String stepDescription = String.format("Creating file system: %s snapshot : %s", fsURI, snapshot);
            Object[] args = new Object[] { storage, snapshot, fsURI };
            _fileDeviceController.createMethod(workflow, null, CREATE_FILESYSTEM_SNAPSHOT_METHOD, snapshotFSStep, stepDescription, storage,
                    args);
            String successMessage = String.format("Creating file system: %s snapshot : %s finished successfully.", fsURI, snapshot);
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error(String.format("Creating file system: %s snapshot : %s failed.", fsURI, snapshot), ex);
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
            stepDescription = String.format("Deleting file system: %s CIFS share: %s ", uri, fileSMBShare.getName());
            successMessage = String.format("Deleting file system: %s CIFS share: %s finished succesfully.", uri, fileSMBShare.getName());
            opName = ResourceOperationTypeEnum.DELETE_FILE_SYSTEM_SHARE.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = String.format("Deleting file system snapshot: %s CIFS share: %s ", uri, fileSMBShare.getName());
            successMessage = String.format("Deleting file system snapshot: %s CIFS share: %s finished succesfully.", uri,
                    fileSMBShare.getName());
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
            s_logger.error(String.format("Deleting file system snapshot: %s CIFS share: %s failed.", uri, fileSMBShare.getName()), ex);
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
            stepDescription = String.format("Deleting export rules for file system : %s ", uri);
            successMessage = String.format("Deleting export rules for file system : %s finished successfully.", uri);
            opName = ResourceOperationTypeEnum.UNEXPORT_FILE_SYSTEM.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, opId);
            fileObj = s_dbClient.queryObject(Snapshot.class, uri);
            stepDescription = String.format("Deleting export rules for file system snapshot : %s ", uri);
            successMessage = String.format("Deleting export rules for file system snapshot : %s finished successfully.", uri);
            opName = ResourceOperationTypeEnum.UNEXPORT_FILE_SNAPSHOT.getName();
        }
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, DELETE_FILESYSTEM_EXPORT_RULES_WF_NAME, false, opId, completer);
            String waitFor = null;
            // Check if the export should be unmounted before deleting
            if (unmountExport) {
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
                waitFor = _fileDeviceController.createMethod(workflow, waitFor, CHECK_IF_EXPORT_IS_MOUNTED, null,
                        "Checking if the export is mounted", storage, args);
            }
            Object[] args = new Object[] { storage, uri, allDirs, subDirs };
            _fileDeviceController.createMethod(workflow, waitFor, DELETE_FILESYSTEM_EXPORT_RULES, null, stepDescription, storage,
                    args);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error(String.format("Deleting export rules for file system snapshot : %s failed. ", uri), ex);
            ServiceError serviceError = DeviceControllerException.errors.deleteExportRuleFailed(fileObj.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }

    }

    @Override
    public void restoreFS(URI storage, URI fs, URI snapshot, String opId) throws ControllerException {
        // Using VNXeFSSnapshotTaskCompleter as it will serve the purpose..
        VNXeFSSnapshotTaskCompleter completer = new VNXeFSSnapshotTaskCompleter(Snapshot.class, snapshot, opId);
        Workflow workflow = null;
        try {
            workflow = this._workflowService.getNewWorkflow(this, RESTORE_FILESYSTEM_SNAPSHOT_WF_NAME, false, opId, completer);
            String restoreFSStep = workflow.createStepId();
            String stepDescription = String.format("Restoring file System : %s from snapshot: %s", fs, snapshot);
            Object[] args = new Object[] { storage, fs, snapshot };
            _fileDeviceController.createMethod(workflow, null, RESTORE_FILESYSTEM_SNAPSHOT_METHOD, restoreFSStep, stepDescription, storage,
                    args);
            String successMessage = String.format("Restoring file system : %s from snapshot: %s finished successfully.", fs, snapshot);
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error(String.format("Restoring file system : %s from snapshot: %s failed.", fs, snapshot), ex);
            String opName = ResourceOperationTypeEnum.RESTORE_FILE_SNAPSHOT.getName();
            ServiceError serviceError = DeviceControllerException.errors.restoreFSFromSnapshotFailed(fs.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    @Override
    public void deleteSnapshot(URI storage, URI pool, URI uri, boolean forceDelete, String deleteType, String opId)
            throws ControllerException {
        Snapshot snap = s_dbClient.queryObject(Snapshot.class, uri);
        FileSnapshotWorkflowCompleter completer = new FileSnapshotWorkflowCompleter(uri, opId);
        Workflow workflow = null;
        try {
            workflow = this._workflowService.getNewWorkflow(this, DELETE_FILESYSTEM_SNAPSHOT_WF_NAME, false, opId, completer);
            String deleteSnapshotStep = workflow.createStepId();
            String stepDescription = String.format("Deleting file System : %s snapshot: %s", snap.getParent(), uri);
            Object[] args = new Object[] { storage, pool, uri, forceDelete, deleteType };
            _fileDeviceController.createMethod(workflow, null, DELETE_FILESYSTEM_SNAPSHOT_METHOD, deleteSnapshotStep, stepDescription,
                    storage, args);
            String successMessage = String.format("Deleting file System : %s snapshot: %s finished successfully.", snap.getParent(), uri);
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error(String.format("Deleting file System : %s snapshot: %s failed.", snap.getParent(), uri), ex);
            String opName = ResourceOperationTypeEnum.DELETE_FILE_SNAPSHOT.getName();
            ServiceError serviceError = DeviceControllerException.errors.deleteFSSnapshotFailed(uri.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    @Override
    public void deleteShareACLs(URI storage, URI uri, String shareName, String taskId) throws ControllerException {
        String stepDescription = null;
        String successMessage = null;
        String opName = null;
        TaskCompleter completer = null;

        if (URIUtil.isType(uri, FileShare.class)) {
            completer = new FileWorkflowCompleter(uri, taskId);

            stepDescription = String.format("Deleting file system : %s share : %s  ACLs", uri, shareName);
            successMessage = String.format("Deleting file system : %s share : %s ACLs finished successfully", uri, shareName);
            opName = ResourceOperationTypeEnum.DELETE_FILE_SYSTEM_SHARE_ACL.getName();

        } else {
            completer = new FileSnapshotWorkflowCompleter(uri, taskId);
            stepDescription = String.format("Deleting file system snapshot : %s share: %s ACLs", uri, shareName);
            successMessage = String.format("Deleting file system snapshot : %s share: %s ACLs: finished successfully", uri, shareName);
            opName = ResourceOperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE_ACL.getName();
        }
        try {
            Workflow workflow = this._workflowService.getNewWorkflow(this, DELETE_FILESYSTEM_SHARE_ACLS_WF_NAME, false, taskId, completer);
            String shareACLDeleteStep = workflow.createStepId();
            Object[] args = new Object[] { storage, uri, shareName };
            _fileDeviceController.createMethod(workflow, null, DELETE_FILESYSTEM_SHARE_ACLS_METHOD, shareACLDeleteStep, stepDescription,
                    storage, args);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error(String.format("Deleting file system snapshot : %s share: %s ACLs failed.", uri, shareName), ex);
            ServiceError serviceError = DeviceControllerException.errors.deleteShareACLFailed(uri.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
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
            Workflow.Method failbackMethod = new Workflow.Method(FAILBACK_FILE_SYSTEM_METHOD,
                    systemSource.getId(), sourceFileShare.getId());
            String waitForFailback = workflow.createStep(null, stepDescription, null, systemSource.getId(),
                    systemSource.getSystemType(), getClass(), failbackMethod, null, failbackStep);

            // Replicate directory quota setting
            stepDescription = String.format(
                    "Replicating directory quota settings from source file system : %s to file target system : %s",
                    sourceFileShare.getId(), targetFileShare.getId());
            Workflow.Method replicateDirQuotaSettingsMethod = new Workflow.Method(REPLICATE_FILESYSTEM_DIRECTORY_QUOTA_SETTINGS_METHOD,
                    systemSource.getId(), targetFileShare.getId());
            String replicateDirQuotaSettingsStep = workflow.createStepId();
            workflow.createStep(null, stepDescription, waitForFailback, systemSource.getId(),
                    systemSource.getSystemType(), getClass(), replicateDirQuotaSettingsMethod, null, replicateDirQuotaSettingsStep);

            if (replicateConfiguration) {

                Map<String, List<NfsACE>> sourceNFSACL = FileOrchestrationUtils.queryNFSACL(sourceFileShare, s_dbClient);
                Map<String, List<NfsACE>> targetNFSACL = FileOrchestrationUtils.queryNFSACL(targetFileShare, s_dbClient);

                if (!sourceNFSACL.isEmpty() || !targetNFSACL.isEmpty()) {

                    stepDescription = String.format("Replicating NFS ACL from source file system : %s to file target system : %s",
                            sourceFileShare.getId(), targetFileShare.getId());
                    Workflow.Method replicateNFSACLsMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_ACLS_METHOD,
                            systemSource.getId(), targetFileShare.getId());
                    String replicateNFSACLsStep = workflow.createStepId();
                    workflow.createStep(null, stepDescription, waitForFailback, systemSource.getId(),
                            systemSource.getSystemType(), getClass(), replicateNFSACLsMethod, null, replicateNFSACLsStep);
                }

                // Replicate NFS export and rules to Target Cluster.
                FSExportMap targetnfsExportMap = targetFileShare.getFsExports();
                FSExportMap sourcenfsExportMap = sourceFileShare.getFsExports();

                if (!(targetnfsExportMap == null && sourcenfsExportMap == null)) {
                    // Both source and target export map shouldn't be null
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

                if (!(targetSMBShareMap == null && sourceSMBShareMap == null)) {
                    // Both source and target share map shouldn't be null
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
        MirrorFileFailoverTaskCompleter failoverCompleter = null;
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

            List<URI> combined = Arrays.asList(sourceFileShare.getId(), targetFileShare.getId());
            failoverCompleter = new MirrorFileFailoverTaskCompleter(FileShare.class, combined, failoverStep);
            stepDescription = String.format("Failover Source File System %s to Target System.", sourceFileShare.getLabel());
            Object[] args = new Object[] { systemTarget.getId(), targetFileShare.getId(), failoverCompleter };
            String waitForFailover = _fileDeviceController.createMethod(workflow, null,
                    FAILOVER_FILE_SYSTEM_METHOD, failoverStep, stepDescription, systemTarget.getId(), args);

            // Replicate quota setting
            stepDescription = String.format(
                    "Replicating directory quota settings from source file system : %s to file target system : %s",
                    sourceFileShare.getId(), targetFileShare.getId());
            Workflow.Method replicateDirQuotaSettingsMethod = new Workflow.Method(REPLICATE_FILESYSTEM_DIRECTORY_QUOTA_SETTINGS_METHOD,
                    systemTarget.getId(), fsURI);
            String replicateDirQuotaSettingsStep = workflow.createStepId();
            workflow.createStep(null, stepDescription, waitForFailover, systemTarget.getId(),
                    systemTarget.getSystemType(), getClass(), replicateDirQuotaSettingsMethod, null, replicateDirQuotaSettingsStep);

            if (replicateConfiguration) {

                Map<String, List<NfsACE>> sourceNFSACL = FileOrchestrationUtils.queryNFSACL(sourceFileShare, s_dbClient);
                Map<String, List<NfsACE>> targetNFSACL = FileOrchestrationUtils.queryNFSACL(targetFileShare, s_dbClient);

                if (!sourceNFSACL.isEmpty() || !targetNFSACL.isEmpty()) {

                    stepDescription = String.format("Replicating NFS ACL from source file system : %s to file target system : %s",
                            sourceFileShare.getId(), targetFileShare.getId());
                    Workflow.Method replicateNFSACLsMethod = new Workflow.Method(REPLICATE_FILESYSTEM_NFS_ACLS_METHOD,
                            systemTarget.getId(), fsURI);
                    String replicateNFSACLsStep = workflow.createStepId();
                    workflow.createStep(null, stepDescription, waitForFailover, systemTarget.getId(),
                            systemTarget.getSystemType(), getClass(), replicateNFSACLsMethod, null, replicateNFSACLsStep);
                }

                SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();
                SMBShareMap targetSMBShareMap = targetFileShare.getSMBFileShares();

                if (sourceSMBShareMap != null || targetSMBShareMap != null) {
                    // Both source and target share map shouldn't be null
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
                    // Both source and target export map shouldn't be null
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

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_CIFS_SHARES_TO_TARGET_WF_NAME, false, taskId, completer);

            SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();
            SMBShareMap targetSMBShareMap = targetFileShare.getSMBFileShares();

            if (sourceSMBShareMap == null && targetSMBShareMap != null) {
                // source file system don't have any CIFS share but target do have share.
                List<SMBFileShare> targetSMBShares = new ArrayList<SMBFileShare>(targetSMBShareMap.values());
                deleteCIFSShareFromTarget(workflow, systemTarget, targetSMBShares, targetFileShare);

            } else if (targetSMBShareMap == null && sourceSMBShareMap != null) {
                // target file system don't have any CIFS share but source do have share
                List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());
                createCIFSShareOnTarget(workflow, systemTarget, sourceSMBShares, cifsPort, targetFileShare, sourceFileShare);

            } else if (targetSMBShareMap != null && sourceSMBShareMap != null) {
                // both source and target file system do have some shares..
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

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_CIFS_SHARE_ACLS_TO_TARGET_WF_NAME, false, taskId, completer);
            SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();

            if (sourceSMBShareMap != null) {
                List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());

                for (SMBFileShare sourceSMBShare : sourceSMBShares) {

                    List<ShareACL> sourceShareACLs = FileOrchestrationUtils.queryShareACLs(sourceSMBShare.getName(),
                            sourceFileShare.getId(), s_dbClient);
                    List<ShareACL> targetShareACLs = FileOrchestrationUtils.queryShareACLs(sourceSMBShare.getName(),
                            targetFileShare.getId(), s_dbClient);

                    if (!sourceShareACLs.isEmpty() && targetShareACLs.isEmpty()) {
                        // target share doesn't have any ACLs but corresponding share on source does have ACL
                        params = new CifsShareACLUpdateParams();
                        ShareACLs shareACLs = new ShareACLs();
                        shareACLs.setShareACLs(sourceShareACLs);
                        params.setAclsToAdd(shareACLs);
                        updateCIFSShareACLOnTarget(workflow, systemTarget, targetFileShare, sourceSMBShare, params);

                    } else if (!targetShareACLs.isEmpty() && sourceShareACLs.isEmpty()) {
                        // source share doesn't have any ACLs but corresponding share on target does have ACL
                        params = new CifsShareACLUpdateParams();
                        ShareACLs shareACLs = new ShareACLs();
                        shareACLs.setShareACLs(targetShareACLs);
                        // TO FIX COP-26361 DU case
                        // params.setAclsToDelete(shareACLs);
                        updateCIFSShareACLOnTarget(workflow, systemTarget, targetFileShare, sourceSMBShare, params);

                    } else if (!targetShareACLs.isEmpty() && !sourceShareACLs.isEmpty()) {
                        // both source and target share have some ACL
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
                            // TO FIX COP-26361 DU case
                            // params.setAclsToDelete(deleteShareACLs);
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
            String successMessage = String.format(
                    "Replicating source File System : %s, CIFS Shares ACLs to Target System finished successfully",
                    sourceFileShare.getLabel());
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem CIFS shares ACLs : " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.updateFileShareCIFSACLsFailed(
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

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_NFS_EXPORT_TO_TARGET_WF_NAME, false, taskId, completer);

            FSExportMap sourceNFSExportMap = sourceFileShare.getFsExports();
            FSExportMap targetNFSExportMap = targetFileShare.getFsExports();

            if (targetNFSExportMap == null && sourceNFSExportMap != null) {
                // No export on target i.e create all source export on target
                List<FileExport> sourceNFSExports = new ArrayList<FileExport>(sourceNFSExportMap.values());
                createNFSExportOnTarget(workflow, systemTarget, sourceNFSExports, nfsPort, targetFileShare, sourceFileShare);

            } else if (sourceNFSExportMap != null && targetNFSExportMap != null) {
                // both source and target have some exports
                List<FileExport> sourceNFSExports = new ArrayList<FileExport>(sourceNFSExportMap.values());
                List<FileExport> targetNFSExports = new ArrayList<FileExport>(targetNFSExportMap.values());

                List<FileExport> targetNFSExportstoCreate = new ArrayList<FileExport>();

                // Creating new map since FSExportMap key contains path+sec+user
                HashMap<String, FileExport> sourceFileExportMap = FileOrchestrationUtils.getFileExportMap(sourceNFSExports);
                HashMap<String, FileExport> targetFileExportMap = FileOrchestrationUtils.getFileExportMap(targetNFSExports);
                String waitFor = null;
                // Check for export to create on target
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
                if (!targetNFSExportstoCreate.isEmpty()) {
                    waitFor = createNFSExportOnTarget(workflow, systemTarget, targetNFSExportstoCreate, nfsPort, targetFileShare,
                            sourceFileShare);
                }

                // Check for export to delete on target
                for (String exportPath : targetFileExportMap.keySet()) {

                    String stepDescription = String.format("deleting NFS export : %s", exportPath);
                    String exportdeletionStep = workflow.createStepId();
                    if (exportPath.equals(targetFileShare.getPath())) {
                        if (sourceFileExportMap.get(sourceFileShare.getPath()) == null) {
                            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), false, null };
                            waitFor = _fileDeviceController.createMethod(workflow, waitFor, DELETE_FILESYSTEM_EXPORT_METHOD,
                                    exportdeletionStep, stepDescription, systemTarget, args);
                        }
                    } else {
                        ArrayList<String> subdirName = new ArrayList<String>();
                        subdirName.add(exportPath.split(targetFileShare.getPath())[1]);
                        if (sourceFileExportMap.get(sourceFileShare.getPath() + subdirName.get(0)) == null) {
                            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), false, subdirName.get(0).substring(1) };
                            waitFor = _fileDeviceController.createMethod(workflow, waitFor, DELETE_FILESYSTEM_EXPORT_METHOD,
                                    exportdeletionStep, stepDescription, systemTarget, args);
                        }
                    }
                }
            }
            String successMessage = String.format(
                    "Replicating source File System : %s NFS Exports to Target System finished successfully", sourceFileShare.getId());
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem NFS Exports : " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.updateFileShareExportRulesFailed(
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

            FSExportMap sourceFSExportMap = sourceFileShare.getFsExports();
            FSExportMap targetFSExportMap = targetFileShare.getFsExports();

            if (sourceFSExportMap == null && targetFSExportMap != null) {
                // There are no export rule on source but there are on target!!
                List<ExportRule> exportRulesToDelete;
                HashMap<String, List<ExportRule>> targetExportRuleMap = FileOrchestrationUtils.getFSExportRuleMap(targetFileShare,
                        s_dbClient);
                for (String exportPath : targetExportRuleMap.keySet()) {
                    FileExportUpdateParams params = new FileExportUpdateParams();

                    if (exportPath.equals(targetFileShare.getPath())) {
                        // File system export rules....
                        exportRulesToDelete = targetExportRuleMap.get(targetFileShare.getPath());

                    } else {
                        // Sub directory export rules....
                        String subDir = exportPath.split(targetFileShare.getPath())[1];
                        exportRulesToDelete = targetExportRuleMap.get(targetFileShare.getPath() + subDir);
                        params.setSubDir(subDir.substring(1));
                    }
                    ExportRules deleteExportRules = new ExportRules();
                    deleteExportRules.setExportRules(exportRulesToDelete);
                    params.setExportRulesToDelete(deleteExportRules);
                    updateFSExportRulesOnTarget(workflow, systemTarget, targetFileShare, exportPath, params);
                }

            } else if (targetFSExportMap != null && sourceFSExportMap != null) {
                // Both source and target have export rules!!
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
                    if (sourceExportRules != null && targetExportRules != null) {
                        srcExportRuleSecFlvMap = FileOrchestrationUtils.getExportRuleSecFlvMap(sourceExportRules);
                        trgtExportRuleSecFlvMap = FileOrchestrationUtils.getExportRuleSecFlvMap(targetExportRules);

                        FileOrchestrationUtils.checkForExportRuleToAdd(sourceFileShare, targetFileShare, srcExportRuleSecFlvMap,
                                trgtExportRuleSecFlvMap, exportRulesToAdd);

                        FileOrchestrationUtils.checkForExportRuleToDelete(srcExportRuleSecFlvMap, trgtExportRuleSecFlvMap,
                                exportRulesToDelete);

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
                            updateFSExportRulesOnTarget(workflow, systemTarget, targetFileShare, exportPath, params);
                        }
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
            ServiceError serviceError = DeviceControllerException.errors.updateFileShareExportRulesFailed(fsURI.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    private static String createNFSExportOnTarget(Workflow workflow, URI systemTarget, List<FileExport> nfsExportsToCreate,
            StoragePort nfsPort, FileShare targetFileShare, FileShare sourceFileShare) {
        String waitFor = null;
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
            waitFor = _fileDeviceController.createMethod(workflow, waitFor, CREATE_FILESYSTEM_EXPORT_METHOD, exportCreationStep,
                    stepDescription, systemTarget, args);
        }
        return waitFor;
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

    private static void updateFSExportRulesOnTarget(Workflow workflow, URI systemTarget, FileShare targetFileShare, String exportPath,
            FileExportUpdateParams params) {
        String stepDescription = String.format("updating NFS export rules for path : %s, %s", exportPath, params.toString());
        String exportRuleUpdateStep = workflow.createStepId();
        Object[] args = new Object[] { systemTarget, targetFileShare.getId(), params };
        _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_EXPORT_RULES_METHOD, exportRuleUpdateStep,
                stepDescription, systemTarget, args);
    }

    private static void updateNFSACLOnTarget(Workflow workflow, URI systemTarget, FileShare targetFileShare,
            FileNfsACLUpdateParams params) {

        String stepDescription = String.format(
                "Updating NFS ACL of file system: %s", targetFileShare.getName(), params.toString());
        String updateNFSACLStep = workflow.createStepId();
        Object[] args = new Object[] { systemTarget, targetFileShare.getId(), params };
        _fileDeviceController.createMethod(workflow, null, UPDATE_FILESYSTEM_NFS_ACL_METHOD, updateNFSACLStep,
                stepDescription, systemTarget, args);
    }

    /**
     * Child workflow for replicating source file system NFS ACL to target system.
     * 
     * @param systemTarget
     *            - URI of target StorageSystem where source CIFS shares has to be replicated.
     * @param fsURI
     *            -URI of the source FileSystem
     * @param taskId
     */
    public void addStepsToReplicateNFSACLs(URI systemTarget, URI fsURI, String taskId) {
        s_logger.info("Generating steps for Replicating NFS ACLs to Target Cluster");
        FileNfsACLUpdateParams params = null;
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

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_NFS_ACLS_TO_TARGET_WF_NAME, false, taskId, completer);

            Map<String, List<NfsACE>> sourceFSACLMap = FileOrchestrationUtils.queryNFSACL(sourceFileShare, s_dbClient);
            Map<String, List<NfsACE>> targetFSACLMap = FileOrchestrationUtils.queryNFSACL(targetFileShare, s_dbClient);

            if (!sourceFSACLMap.isEmpty() && targetFSACLMap.isEmpty()) {
                // target share doesn't have any ACLs but corresponding share on source does have ACL
                s_logger.info("Target NFS doesn't have any ACL but corresponding NFS on source does have ACL.");
                for (String fsPath : sourceFSACLMap.keySet()) {
                    List<NfsACE> aclToAdd = null;
                    params = FileOrchestrationUtils.getFileNfsACLUpdateParamWithSubDir(fsPath, sourceFileShare);
                    aclToAdd = sourceFSACLMap.get(fsPath);
                    params.setAcesToAdd(aclToAdd);
                    s_logger.info("Invoking updateNFSACL on FS: {}, with {}", targetFileShare.getName(), params);
                    updateNFSACLOnTarget(workflow, systemTarget, targetFileShare, params);
                }
            } else if (!targetFSACLMap.isEmpty() && sourceFSACLMap.isEmpty()) {
                s_logger.info("Source NFS doesn't have any ACL but corresponding NFS on target has ACL.");
                for (String fsPath : targetFSACLMap.keySet()) {
                    List<NfsACE> aclToDelete = null;
                    params = FileOrchestrationUtils.getFileNfsACLUpdateParamWithSubDir(fsPath, targetFileShare);
                    aclToDelete = targetFSACLMap.get(fsPath);
                    // TO FIX COP-26361 DU case
                    // params.setAcesToDelete(aclToDelete);
                    s_logger.info("Invoking updateNFSACL on FS: {}, with {}", targetFileShare.getName(), params);
                    updateNFSACLOnTarget(workflow, systemTarget, targetFileShare, params);
                }
            } else if (!sourceFSACLMap.isEmpty() && !targetFSACLMap.isEmpty()) {
                // both source and target FS have some ACL
                for (String sourceFSACLPath : sourceFSACLMap.keySet()) {

                    List<NfsACE> aclToAdd = new ArrayList<NfsACE>();
                    List<NfsACE> aclToDelete = new ArrayList<NfsACE>();
                    List<NfsACE> aclToModify = new ArrayList<NfsACE>();

                    // Segregate source and target NFS ACL
                    params = FileOrchestrationUtils.getFileNfsACLUpdateParamWithSubDir(sourceFSACLPath, sourceFileShare);
                    List<NfsACE> sourceNFSACL = sourceFSACLMap.get(sourceFSACLPath);
                    if (sourceNFSACL == null) {
                        sourceNFSACL = new ArrayList<NfsACE>();
                    }
                    String subDir = params.getSubDir();
                    String targetFSACLPath = targetFileShare.getPath();
                    if (subDir != null) {
                        targetFSACLPath += "/" + subDir;
                    }
                    List<NfsACE> targetNFSACL = targetFSACLMap.get(targetFSACLPath);

                    if (targetNFSACL == null) {
                        targetNFSACL = new ArrayList<NfsACE>();
                    }

                    HashMap<String, NfsACE> sourceUserToNFSACLMap = FileOrchestrationUtils
                            .getUserToNFSACEMap(sourceNFSACL);

                    HashMap<String, NfsACE> targetUserToNFSACLMap = FileOrchestrationUtils
                            .getUserToNFSACEMap(targetNFSACL);

                    // ACL To Add
                    for (String sourceACEUser : sourceUserToNFSACLMap.keySet()) {
                        if (targetUserToNFSACLMap.get(sourceACEUser) == null) {
                            NfsACE nfsACE = sourceUserToNFSACLMap.get(sourceACEUser);
                            aclToAdd.add(nfsACE);
                        }
                    }

                    // ACL To Delete
                    for (String targetACEUser : targetUserToNFSACLMap.keySet()) {
                        if (sourceUserToNFSACLMap.get(targetACEUser) == null) {
                            aclToDelete.add(targetUserToNFSACLMap.get(targetACEUser));
                        }
                    }

                    // ACL to Modify
                    targetNFSACL.removeAll(aclToDelete);
                    sourceNFSACL.removeAll(aclToAdd);

                    sourceUserToNFSACLMap = FileOrchestrationUtils.getUserToNFSACEMap(sourceNFSACL);
                    targetUserToNFSACLMap = FileOrchestrationUtils.getUserToNFSACEMap(targetNFSACL);

                    for (String sourceACEUser : sourceUserToNFSACLMap.keySet()) {

                        NfsACE targetACE = targetUserToNFSACLMap.get(sourceACEUser);
                        NfsACE sourceACE = sourceUserToNFSACLMap.get(sourceACEUser);

                        if (targetACE != null &&
                                (!targetACE.getPermissions().equals(sourceACE.getPermissions()) ||
                                        !targetACE.getPermissionType().equals(sourceACE.getPermissionType()))) {

                            targetACE.setPermissions(sourceACE.getPermissions());
                            targetACE.setPermissionType(sourceACE.getPermissionType());
                            aclToModify.add(targetACE);
                        }
                    }

                    if (!aclToAdd.isEmpty()) {
                        params.setAcesToAdd(aclToAdd);
                    }
                    if (!aclToDelete.isEmpty()) {
                        // TO FIX COP-26361 DU case
                        // params.setAcesToDelete(aclToDelete);
                    }
                    if (!aclToModify.isEmpty()) {
                        params.setAcesToModify(aclToModify);
                    }

                    if (!params.retrieveAllACL().isEmpty()) {
                        s_logger.info("Invoking updateNFSACL on FS: {}, with {}", targetFileShare.getName(), params);
                        updateNFSACLOnTarget(workflow, systemTarget, targetFileShare, params);
                    }
                }
            }

            String successMessage = String.format(
                    "Replicating source file system : %s, NFS ACL to target file system finished successfully",
                    sourceFileShare.getLabel());
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem NFS ACL : " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.updateFileShareNFSACLFailed(
                    fsURI.toString(), opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    private static void updateTargetFileSystem(Workflow workflow, URI systemTarget, FileShare targetFileShare) {

        String stepDescription = String.format(
                "Updating target file system: %s with new directory quota settings.", targetFileShare.getName());
        String updateQuotaDirStep = workflow.createStepId();
        Object[] args = new Object[] { systemTarget, targetFileShare.getPool(), targetFileShare.getId() };
        _fileDeviceController.createMethod(workflow, null, MODIFY_FILESYSTEM_METHOD, updateQuotaDirStep,
                stepDescription, systemTarget, args);
    }

    /**
     * Child workflow for replicating source file system directory quota settings to target system.
     * 
     * @param systemTarget
     *            - URI of target StorageSystem where source quota directory settings have to be replicated.
     * @param fsURI
     *            -URI of the source FileSystem
     * @param taskId
     */
    public void addStepsToReplicateDirectoryQuotaSettings(URI systemTarget, URI fsURI, String taskId) {
        s_logger.info("Generating steps for replicating directory quota settings to target cluster.");
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
            targetFileShare.setSoftGracePeriod(sourceFileShare.getSoftGracePeriod());
            targetFileShare.setSoftLimit(sourceFileShare.getSoftLimit());
            targetFileShare.setNotificationLimit(sourceFileShare.getNotificationLimit());
            s_dbClient.updateObject(targetFileShare);

            workflow = this._workflowService.getNewWorkflow(this, REPLICATE_QUOTA_DIR_SETTINGS_TO_TARGET_WF_NAME, false, taskId, completer);
            updateTargetFileSystem(workflow, systemTarget, targetFileShare);
            String successMessage = String.format(
                    "Replicating source file system : %s, directory quota settings to target file system finished successfully.",
                    sourceFileShare.getLabel());
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem directory quota settings: " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILOVER.getName();
            ServiceError serviceError = DeviceControllerException.errors.unableToUpdateFileSystem(opName, ex);
            completer.error(s_dbClient, this._locker, serviceError);
        }
    }

    public String addStepsForApplyingPolicies(Workflow workflow, String waitFor, List<FileDescriptor> fileDescriptors) {

        FileDescriptor sourceDescriptors = FileDescriptor
                .filterByType(fileDescriptors, FileDescriptor.Type.FILE_DATA, FileDescriptor.Type.FILE_MIRROR_SOURCE).get(0);
        FileShare sourceFS = s_dbClient.queryObject(FileShare.class, sourceDescriptors.getFsURI());
        StorageSystem system = s_dbClient.queryObject(StorageSystem.class, sourceFS.getStorageDevice());
        // applying policy is only supported by isilon
        if (system != null && system.getSystemType().equalsIgnoreCase(Type.isilon.toString())) {
            URI nasServer = null;
            if (sourceFS.getVirtualNAS() != null) {
                nasServer = sourceFS.getVirtualNAS();
            } else {
                // Get the physical NAS for the storage system!!
                PhysicalNAS pNAS = FileOrchestrationUtils.getSystemPhysicalNAS(s_dbClient, system);
                if (pNAS != null) {
                    nasServer = pNAS.getId();
                }
            }

            if (nasServer == null) {
                s_logger.error(
                        String.format("Adding steps to apply policies failed : No Nas server found on system {}", system.getLabel()));
                throw DeviceControllerException.exceptions.noNasServerFoundToAddStepsToApplyPolicy(system.getLabel());
            }

            VirtualPool vpool = s_dbClient.queryObject(VirtualPool.class, sourceFS.getVirtualPool());
            List<FilePolicy> fileVpoolPolicies = new ArrayList<FilePolicy>();
            waitFor = setVpoolLevelPolicesToCreate(workflow, vpool,
                    sourceFS.getStorageDevice(),
                    nasServer, fileVpoolPolicies, waitFor);
            if (fileVpoolPolicies != null && !fileVpoolPolicies.isEmpty()) {
                for (FilePolicy fileVpoolPolicy : fileVpoolPolicies) {
                    String stepDescription = String.format("creating file policy : %s  at : %s level", fileVpoolPolicy.getId(),
                            vpool.getLabel());
                    String applyFilePolicyStep = workflow.createStepId();
                    Object[] args = new Object[] { sourceFS.getStorageDevice(), sourceFS.getId(), fileVpoolPolicy.getId() };
                    waitFor = _fileDeviceController.createMethod(workflow, waitFor, APPLY_FILE_POLICY_METHOD, applyFilePolicyStep,
                            stepDescription, system.getId(), args);
                }
            }

            Project project = s_dbClient.queryObject(Project.class, sourceFS.getProject());
            List<FilePolicy> fileProjectPolicies = new ArrayList<FilePolicy>();
            waitFor = setAllProjectLevelPolices(workflow, project, vpool,
                    sourceFS.getStorageDevice(), nasServer, fileProjectPolicies, waitFor);

            if (fileProjectPolicies != null && !fileProjectPolicies.isEmpty()) {
                for (FilePolicy fileProjectPolicy : fileProjectPolicies) {
                    String stepDescription = String.format("creating file policy : %s  at : %s level", fileProjectPolicy.getId(),
                            project.getLabel());
                    String applyFilePolicyStep = workflow.createStepId();
                    Object[] args = new Object[] { sourceFS.getStorageDevice(), sourceFS.getId(), fileProjectPolicy.getId() };
                    waitFor = _fileDeviceController.createMethod(workflow, waitFor, APPLY_FILE_POLICY_METHOD, applyFilePolicyStep,
                            stepDescription, system.getId(), args);
                }
            }
        }
        return waitFor;

    }

    @Override
    public void unassignFilePolicy(URI policy, Set<URI> unassignFrom, String taskId) throws InternalException {
        FilePolicy filePolicy = s_dbClient.queryObject(FilePolicy.class, policy);
        FilePolicyUnAssignWorkflowCompleter completer = new FilePolicyUnAssignWorkflowCompleter(policy, unassignFrom, taskId);
        try {
            Workflow workflow = _workflowService.getNewWorkflow(this, UNASSIGN_FILE_POLICY_WF_NAME, false, taskId, completer);
            completer.setWorkFlowId(workflow.getWorkflowURI());

            s_logger.info("Generating steps for unassigning file policy {} from resources", policy);
            Set<String> policyResources = filePolicy.getPolicyStorageResources();
            if (policyResources != null && !policyResources.isEmpty()) {
                for (URI uri : unassignFrom) {
                    for (String policyResource : policyResources) {
                        PolicyStorageResource policyStorage = s_dbClient.queryObject(PolicyStorageResource.class,
                                URI.create(policyResource));
                        if (policyStorage.getAppliedAt().toString().equals(uri.toString())) {
                            StorageSystem storageSystem = s_dbClient.queryObject(StorageSystem.class, policyStorage.getStorageSystem());
                            String stepId = workflow.createStepId();
                            String stepDes = String.format("unassigning file policy : %s,  from resource: %s,", filePolicy.getId(), uri);
                            Object[] args = new Object[] { storageSystem.getId(), policy, policyStorage.getId() };
                            _fileDeviceController.createMethod(workflow, null, UNASSIGN_FILE_POLICY_METHOD, stepId, stepDes,
                                    storageSystem.getId(), args);
                        }
                    }
                }
            } else {
                s_logger.info("file policy {} is not applied to any storage system", policy);
                for (URI uri : unassignFrom) {
                    filePolicy.removeAssignedResources(uri);
                    FileOrchestrationUtils.updateUnAssignedResource(filePolicy, uri, s_dbClient);
                }

                // If no other resources are assigned to replication policy
                // Remove the replication topology from the policy
                FileOrchestrationUtils.removeTopologyInfo(filePolicy, s_dbClient);
                s_dbClient.updateObject(filePolicy);
                s_logger.info("Unassigning file policy: {} from resources: {} finished successfully", policy.toString(),
                        unassignFrom.toString());
            }
            String successMessage = String.format("unassigning file policy : %s,  from resources: %s finsihed successfully,",
                    filePolicy.getId(), unassignFrom);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error(String.format("unassigning file policy : %s,  from resource: %s failed,", filePolicy.getId(), unassignFrom), ex);
            ServiceError serviceError = DeviceControllerException.errors.unassignFilePolicyFailed(policy.toString(), ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void assignFileSnapshotPolicyToVirtualPools(Map<URI, List<URI>> vpoolToStorageSystemMap, URI filePolicyToAssign, String taskId)
            throws InternalException {
        FilePolicy filePolicy = s_dbClient.queryObject(FilePolicy.class, filePolicyToAssign);
        FilePolicyAssignWorkflowCompleter completer = new FilePolicyAssignWorkflowCompleter(filePolicyToAssign,
                vpoolToStorageSystemMap.keySet(), null, taskId);

        try {
            String waitFor = null;
            Workflow workflow = _workflowService.getNewWorkflow(this, ASSIGN_FILE_POLICY_WF_NAME, false, taskId, completer);
            completer.setWorkFlowId(workflow.getWorkflowURI());

            String usePhysicalNASForProvisioning = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.USE_PHYSICAL_NAS_FOR_PROVISIONING, "isilon", null);
            Boolean usePhysicalNAS = Boolean.valueOf(usePhysicalNASForProvisioning);

            for (URI vpoolURI : vpoolToStorageSystemMap.keySet()) {
                s_logger.info("Generating steps for assigning file policy {} to vpool: {}.", filePolicyToAssign, vpoolURI);
                List<URI> storageSystemURIList = vpoolToStorageSystemMap.get(vpoolURI);
                if (storageSystemURIList != null && !storageSystemURIList.isEmpty()) {
                    for (URI storageSystemURI : storageSystemURIList) {

                        List<URI> vNASURIList = FileOrchestrationUtils
                                .getVNASServersOfStorageSystemAndVarrayOfVpool(s_dbClient, storageSystemURI, vpoolURI, null);
                        if (vNASURIList != null && !vNASURIList.isEmpty()) {
                            for (URI vNASURI : vNASURIList) {
                                String stepId = workflow.createStepId();
                                String stepDes = String
                                        .format("Assigning file policy: %s, to vpool: %s on storage system: %s", filePolicy.getId(),
                                                vpoolURI,
                                                storageSystemURI);
                                Object[] args = new Object[] { storageSystemURI, vNASURI, filePolicyToAssign, vpoolURI };
                                // Let the all workflow steps be executed
                                // workflow completer should handle the unsuccessful steps
                                _fileDeviceController.createMethod(workflow, waitFor,
                                        ASSIGN_FILE_SNAPSHOT_POLICY_TO_VIRTUAL_POOLS_METHOD,
                                        stepId,
                                        stepDes,
                                        storageSystemURI, args);
                            }
                        }

                        StorageSystem storagesystem = s_dbClient.queryObject(StorageSystem.class, storageSystemURI);
                        if (storagesystem.getSystemType().equals(Type.isilon.toString())) {

                            if (usePhysicalNAS) {
                                s_logger.info("Generating step for assigning file policy {} to vpool on physical NAS server: {}.",
                                        filePolicyToAssign, vpoolURI);
                                String stepId = workflow.createStepId();
                                String stepDes = String
                                        .format("Assigning file policy: %s, to vpool: %s on storage system: %s", filePolicy.getId(),
                                                vpoolURI,
                                                storageSystemURI);
                                Object[] args = new Object[] { storageSystemURI, null, filePolicyToAssign, vpoolURI };
                                // Let the all workflow steps be executed
                                // workflow completer should handle the unsuccessful steps
                                _fileDeviceController.createMethod(workflow, waitFor,
                                        ASSIGN_FILE_SNAPSHOT_POLICY_TO_VIRTUAL_POOLS_METHOD,
                                        stepId,
                                        stepDes,
                                        storageSystemURI, args);
                            }
                        }

                    }
                } else {
                    s_logger.info("No storage system(s) present for vpool: {}", vpoolURI);
                }
            }

            String successMessage = String.format("Assigning file policy : %s, to vpool(s) successful.",
                    filePolicy.getId(), vpoolToStorageSystemMap);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error(String.format("Assigning file policy : %s to vpool(s) failed", filePolicy.getId()), ex);
            ServiceError serviceError = DeviceControllerException.errors
                    .assignFilePolicyFailed(filePolicyToAssign.toString(), FilePolicyApplyLevel.vpool.name(), ex);
            completer.error(s_dbClient, _locker, serviceError);
        }

    }

    @Override
    public void assignFileSnapshotPolicyToProjects(Map<URI, List<URI>> vpoolToStorageSystemMap, List<URI> projectURIs,
            URI filePolicyToAssign, String taskId) {
        FilePolicy filePolicy = s_dbClient.queryObject(FilePolicy.class, filePolicyToAssign);
        String opName = ResourceOperationTypeEnum.ASSIGN_FILE_POLICY.getName();
        URI projectVpool = null;
        if (vpoolToStorageSystemMap != null && !vpoolToStorageSystemMap.isEmpty()) {
            Set<URI> vpoolUris = vpoolToStorageSystemMap.keySet();
            // For project assignment, there would be a single vpool!!
            projectVpool = vpoolUris.toArray(new URI[vpoolUris.size()])[0];
        }

        FilePolicyAssignWorkflowCompleter completer = new FilePolicyAssignWorkflowCompleter(filePolicyToAssign, projectURIs, projectVpool,
                taskId);

        try {
            String waitFor = null;
            Workflow workflow = _workflowService.getNewWorkflow(this, ASSIGN_FILE_POLICY_WF_NAME, false, taskId, completer);
            completer.setWorkFlowId(workflow.getWorkflowURI());

            String usePhysicalNASForProvisioning = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.USE_PHYSICAL_NAS_FOR_PROVISIONING, "isilon", null);
            Boolean usePhysicalNAS = Boolean.valueOf(usePhysicalNASForProvisioning);

            for (URI vpoolURI : vpoolToStorageSystemMap.keySet()) {
                s_logger.info("Generating steps for assigning file policy {} to project: {}.", filePolicyToAssign, vpoolURI);
                List<URI> storageSystemURIList = vpoolToStorageSystemMap.get(vpoolURI);
                if (storageSystemURIList != null && !storageSystemURIList.isEmpty()) {
                    for (URI storageSystemURI : storageSystemURIList) {

                        if (projectURIs != null && !projectURIs.isEmpty()) {

                            for (URI projectURI : projectURIs) {
                                // Get the eligible nas server for given project from the storage system!!!
                                List<URI> vNASURIList = FileOrchestrationUtils.getVNASServersOfStorageSystemAndVarrayOfVpool(s_dbClient,
                                        storageSystemURI, vpoolURI, projectURI);
                                if (vNASURIList != null && !vNASURIList.isEmpty()) {
                                    for (URI vNASURI : vNASURIList) {
                                        String stepId = workflow.createStepId();
                                        String stepDes = String
                                                .format("Assigning file policy: %s, to project: %s on storage system: %s",
                                                        filePolicy.getId(),
                                                        vpoolURI,
                                                        storageSystemURI);
                                        Object[] args = new Object[] { storageSystemURI, vNASURI, filePolicyToAssign, vpoolURI,
                                                projectURI };
                                        // Let the all workflow steps be executed
                                        // workflow completer should handle the unsuccessful steps
                                        _fileDeviceController.createMethod(workflow, waitFor,
                                                ASSIGN_FILE_SNAPSHOT_POLICY_TO_PROJECTS_METHOD,
                                                stepId,
                                                stepDes,
                                                storageSystemURI, args);
                                    }
                                }

                                StorageSystem storagesystem = s_dbClient.queryObject(StorageSystem.class, storageSystemURI);

                                // Create policy, if physical nas is eligible for provisioning!!
                                if (storagesystem.getSystemType().equals(Type.isilon.toString())) {

                                    if (usePhysicalNAS) {
                                        s_logger.info(
                                                "Generating step for assigning file policy {} to project on physical NAS server: {}.",
                                                filePolicyToAssign, vpoolURI);
                                        String stepId = workflow.createStepId();
                                        String stepDes = String
                                                .format("Assigning file policy: %s, to project: %s on storage system: %s",
                                                        filePolicy.getId(),
                                                        projectURI,
                                                        storageSystemURI);
                                        Object[] args = new Object[] { storageSystemURI, null, filePolicyToAssign, vpoolURI, projectURI };
                                        // Let the all workflow steps be executed
                                        // workflow completer should handle the unsuccessful steps
                                        _fileDeviceController.createMethod(workflow, waitFor,
                                                ASSIGN_FILE_SNAPSHOT_POLICY_TO_PROJECTS_METHOD,
                                                stepId,
                                                stepDes,
                                                storageSystemURI, args);

                                    }
                                }
                            }
                        }
                    }
                } else {
                    s_logger.info("No storage system(s) present for vpool: {}", vpoolURI);
                }
            }

            String successMessage = String.format("Assigning file policy : %s, to project(s) successful.",
                    filePolicy.getId(), vpoolToStorageSystemMap);
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            s_logger.error(String.format("Assigning file policy : %s to vpool(s) failed", filePolicy.getId()), ex);
            ServiceError serviceError = DeviceControllerException.errors
                    .assignFilePolicyFailed(filePolicyToAssign.toString(), FilePolicyApplyLevel.project.name(), ex);
            completer.error(s_dbClient, _locker, serviceError);
        }

    }

    @Override
    public void updateFileProtectionPolicy(URI policy, FilePolicyUpdateParam param, String taskId) {
        FilePolicy filePolicy = s_dbClient.queryObject(FilePolicy.class, policy);
        String opName = ResourceOperationTypeEnum.UPDATE_FILE_PROTECTION_POLICY.getName();
        FileProtectionPolicyUpdateCompleter completer = new FileProtectionPolicyUpdateCompleter(policy, taskId);

        try {
            String waitFor = null;
            Workflow workflow = _workflowService.getNewWorkflow(this, UPDATE_FILE_POLICY_WF_NAME, false, taskId, completer);
            completer.setWorkFlowId(workflow.getWorkflowURI());
            // Get the file policy storage resources!!!
            List<PolicyStorageResource> policyStorageResources = FileOrchestrationUtils.getFilePolicyStorageResources(s_dbClient,
                    filePolicy);
            if (policyStorageResources != null && !policyStorageResources.isEmpty()) {
                s_logger.info("Generating steps for updating file policy {} ", filePolicy.getFilePolicyName());
                for (PolicyStorageResource policyStorageRes : policyStorageResources) {
                    StorageSystem system = s_dbClient.queryObject(StorageSystem.class, policyStorageRes.getStorageSystem());
                    String stepId = workflow.createStepId();
                    String stepDes = String
                            .format("Updating policy on storage system %s, at path: %s",
                                    system.getLabel(),
                                    policyStorageRes.getResourcePath());
                    Object[] args = new Object[] { policyStorageRes.getStorageSystem(), policy, policyStorageRes.getId(), param };
                    // Try to update all storage system policies
                    // Dont use waitFor for next step!!!
                    _fileDeviceController.createMethod(workflow, waitFor,
                            UPDATE_STORAGE_SYSTEM_FILE_PROTECTION_POLICY_METHOD,
                            stepId,
                            stepDes,
                            policyStorageRes.getStorageSystem(), args);
                }
                String successMessage = String.format("Updating file policy {} is successful.", filePolicy.getFilePolicyName());
                workflow.executePlan(completer, successMessage);

            } else {
                s_logger.info("No File Policy Storage resource for policy {} to update", filePolicy.getFilePolicyName());
            }

        } catch (Exception ex) {
            s_logger.error(String.format("Updating file protection policy {} failed", filePolicy.getFilePolicyName()), ex);
            ServiceError serviceError = DeviceControllerException.errors
                    .updateFilePolicyFailed(filePolicy.toString(), ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    private Map<URI, List<FileStorageSystemAssociation>>
            getAssociationsPerAssignedResource(List<FileStorageSystemAssociation> associations) {

        Map<URI, List<FileStorageSystemAssociation>> resAssociations = new HashMap<URI, List<FileStorageSystemAssociation>>();
        for (FileStorageSystemAssociation association : associations) {
            List<FileStorageSystemAssociation> recs = resAssociations.get(association.getAppliedAtResource());
            if (recs == null) {
                recs = new ArrayList<FileStorageSystemAssociation>();
                resAssociations.put(association.getAppliedAtResource(), recs);
            }
            recs.add(association);
        }
        return resAssociations;
    }

    private boolean checkRecommendationsWithManySourceToOneTarget(List<FileStorageSystemAssociation> associations) {
        StringSet targets = new StringSet();
        for (Map.Entry<URI, List<FileStorageSystemAssociation>> entry : getAssociationsPerAssignedResource(associations).entrySet()) {
            // Verify more recommnedations are pointing to same target!!!
            if (entry.getValue() != null && entry.getValue().size() < 2) {
                continue;
            }
            for (FileStorageSystemAssociation association : entry.getValue()) {
                StorageSystem system = s_dbClient.queryObject(StorageSystem.class, association.getSourceSystem());
                if (system != null && system.getSystemType().equalsIgnoreCase(Type.isilon.toString()) &&
                        association.getTargets() != null && !association.getTargets().isEmpty()) {
                    for (TargetAssociation target : association.getTargets()) {
                        StringBuffer targetKey = new StringBuffer();
                        if (target.getStorageSystemURI() != null) {
                            targetKey.append(target.getStorageSystemURI().toString());
                        }
                        if (target.getvNASURI() != null) {
                            targetKey.append(target.getvNASURI().toString());
                        }
                        if (!targetKey.toString().isEmpty()) {
                            if (targets.contains(targetKey.toString())) {
                                s_logger.info("Found same taget for different source recommendations");
                                return true;
                            }
                            targets.add(targetKey.toString());
                        }
                    }
                }
            }
        }
        return false;
    }

    private void verifyClusterNameInPathForManyToOneRecommendations(List<FileStorageSystemAssociation> associations,
            FilePolicy filePolicy) {
        if (checkRecommendationsWithManySourceToOneTarget(associations)) {
            StringMap scope = new StringMap();
            scope.put("systemType", "isilon");
            String customConfig = customConfigHandler.getCustomConfigValue(CustomConfigConstants.ISILON_PATH_CUSTOMIZATION,
                    scope);
            if (customConfig != null && !customConfig.isEmpty() && !customConfig.contains("isilon_cluster_name")) {
                s_logger.error(
                        "Conflicting taget path for different sources , Please configure cluster name in directory path defination");
                throw DeviceControllerException.exceptions.assignFilePolicyFailed(filePolicy.getFilePolicyName(),
                        filePolicy.getApplyAt(),
                        "Conflicting taget path for different sources , Please configure cluster name in directory path defination");
            }
        }
    }

    @Override
    public void assignFileReplicationPolicyToVirtualPools(List<FileStorageSystemAssociation> associations,
            List<URI> vpoolURIs, URI filePolicyToAssign, String taskId) {

        FilePolicy filePolicy = s_dbClient.queryObject(FilePolicy.class, filePolicyToAssign);
        FilePolicyAssignWorkflowCompleter completer = new FilePolicyAssignWorkflowCompleter(filePolicyToAssign, vpoolURIs, null, taskId);

        try {
            String waitFor = null;
            String stepId = null;
            String stepDes = null;
            Workflow workflow = _workflowService.getNewWorkflow(this, ASSIGN_FILE_POLICY_WF_NAME, false, taskId, completer);
            completer.setWorkFlowId(workflow.getWorkflowURI());

            String usePhysicalNASForProvisioning = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.USE_PHYSICAL_NAS_FOR_PROVISIONING, "isilon", null);
            Boolean usePhysicalNAS = Boolean.valueOf(usePhysicalNASForProvisioning);

            // Verify the associations have many to one storage system relation.
            // If so, inform the user to configure cluster name in provisioning path!!
            verifyClusterNameInPathForManyToOneRecommendations(associations, filePolicy);

            s_logger.info("Generating steps for assigning file replication policy to vpool: {}.", filePolicyToAssign);

            for (FileStorageSystemAssociation association : associations) {
                StorageSystem sourceStoragesystem = s_dbClient.queryObject(StorageSystem.class, association.getSourceSystem());

                URI vpoolURI = association.getAppliedAtResource();
                List<TargetAssociation> targetAssociations = association.getTargets();
                if (targetAssociations != null && !targetAssociations.isEmpty()) {
                    for (Iterator<TargetAssociation> iterator = targetAssociations.iterator(); iterator.hasNext();) {
                        TargetAssociation targetAssociation = iterator.next();

                        URI targetVNASURI = targetAssociation.getvNASURI();
                        URI targetStorage = targetAssociation.getStorageSystemURI();
                        URI targetVArray = targetAssociation.getvArrayURI();

                        if (targetVNASURI != null && association.getSourceVNAS() != null) {
                            stepId = workflow.createStepId();
                            stepDes = String.format("Assigning file policy: %s, to vpool: %s on storage system: %s",
                                    filePolicy.getId(),
                                    vpoolURI, association.getSourceSystem());

                            Object[] args = new Object[] { association.getSourceSystem(), targetStorage,
                                    association.getSourceVNAS(), targetVArray, targetVNASURI, filePolicyToAssign, vpoolURI };
                            _fileDeviceController.createMethod(workflow, waitFor,
                                    ASSIGN_FILE_REPLICATION_POLICY_TO_VIRTUAL_POOLS_METHOD,
                                    stepId,
                                    stepDes,
                                    association.getSourceSystem(), args);

                        } else {
                            if (sourceStoragesystem.getSystemType().equals(Type.isilon.toString())) {
                                if (usePhysicalNAS) {
                                    stepId = workflow.createStepId();
                                    stepDes = String.format("Assigning file policy: %s, to vpool: %s on storage system: %s",
                                            filePolicy.getId(),
                                            vpoolURI, association.getSourceSystem());

                                    // Let the all workflow steps be executed
                                    // workflow completer should handle the unsuccessful steps
                                    Object[] args = new Object[] { association.getSourceSystem(), targetStorage,
                                            association.getSourceVNAS(), targetVArray, null, filePolicyToAssign, vpoolURI };
                                    _fileDeviceController.createMethod(workflow, waitFor,
                                            ASSIGN_FILE_REPLICATION_POLICY_TO_VIRTUAL_POOLS_METHOD,
                                            stepId,
                                            stepDes,
                                            association.getSourceSystem(), args);
                                }
                            }
                        }
                    }
                }
            }

            String successMessage = String.format("Assigning file policy : %s, to vpool(s) successful.",
                    filePolicy.getId());
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            // If no other resources are assigned to replication policy
            // Remove the replication topology from the policy
            FileOrchestrationUtils.removeTopologyInfo(filePolicy, s_dbClient);

            s_logger.error(String.format("Assigning file policy : %s to vpool(s) failed", filePolicy.getId()), ex);
            ServiceError serviceError = DeviceControllerException.errors
                    .assignFilePolicyFailed(filePolicyToAssign.toString(), filePolicy.getApplyAt(), ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void assignFileReplicationPolicyToProjects(List<FileStorageSystemAssociation> associations, URI vpoolURI, List<URI> projectURIs,
            URI filePolicyToAssign,
            String taskId) {
        FilePolicy filePolicy = s_dbClient.queryObject(FilePolicy.class, filePolicyToAssign);
        FilePolicyAssignWorkflowCompleter completer = new FilePolicyAssignWorkflowCompleter(filePolicyToAssign, projectURIs, vpoolURI,
                taskId);

        try {
            String waitFor = null;
            String stepId = null;
            String stepDes = null;
            Workflow workflow = _workflowService.getNewWorkflow(this, ASSIGN_FILE_POLICY_WF_NAME, false, taskId, completer);
            completer.setWorkFlowId(workflow.getWorkflowURI());

            String usePhysicalNASForProvisioning = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.USE_PHYSICAL_NAS_FOR_PROVISIONING, "isilon", null);
            Boolean usePhysicalNAS = Boolean.valueOf(usePhysicalNASForProvisioning);

            // Verify the associations have many to one storage system relation.
            // If so, inform the user to configure cluster name in provisioning path!!
            verifyClusterNameInPathForManyToOneRecommendations(associations, filePolicy);

            s_logger.info("Generating steps for assigning file policy {} to project.", filePolicyToAssign);

            for (FileStorageSystemAssociation association : associations) {
                StorageSystem sourceStoragesystem = s_dbClient.queryObject(StorageSystem.class, association.getSourceSystem());

                URI projectURI = association.getAppliedAtResource();
                URI vPoolURI = association.getProjectvPool();
                List<TargetAssociation> targetAssociations = association.getTargets();
                if (targetAssociations != null && !targetAssociations.isEmpty()) {
                    for (Iterator<TargetAssociation> iterator = targetAssociations.iterator(); iterator.hasNext();) {
                        TargetAssociation targetAssociation = iterator.next();

                        URI targetVNASURI = targetAssociation.getvNASURI();
                        URI targetStorage = targetAssociation.getStorageSystemURI();
                        URI targetVArray = targetAssociation.getvArrayURI();

                        if (targetVNASURI != null && association.getSourceVNAS() != null) {
                            stepId = workflow.createStepId();
                            stepDes = String.format("Assigning file policy: %s, to project: %s on storage system: %s",
                                    filePolicy.getId(),
                                    projectURI, association.getSourceSystem());

                            // Let the all workflow steps be executed
                            // workflow completer should handle the unsuccessful steps
                            Object[] args = new Object[] { association.getSourceSystem(), targetStorage,
                                    association.getSourceVNAS(), targetVArray, targetVNASURI, filePolicyToAssign, vPoolURI, projectURI };
                            _fileDeviceController.createMethod(workflow, waitFor,
                                    ASSIGN_FILE_REPLICATION_POLICY_TO_PROJECTS_METHOD,
                                    stepId,
                                    stepDes,
                                    association.getSourceSystem(), args);

                        } else {
                            if (sourceStoragesystem.getSystemType().equals(Type.isilon.toString())) {
                                if (usePhysicalNAS) {
                                    stepId = workflow.createStepId();
                                    stepDes = String.format("Assigning file policy: %s, to project: %s on storage system: %s",
                                            filePolicy.getId(),
                                            projectURI, association.getSourceSystem());
                                    // Let the all workflow steps be executed
                                    // workflow completer should handle the unsuccessful steps
                                    Object[] args = new Object[] { association.getSourceSystem(), targetStorage,
                                            association.getSourceVNAS(), targetVArray, null, filePolicyToAssign, vPoolURI, projectURI };
                                    _fileDeviceController.createMethod(workflow, waitFor,
                                            ASSIGN_FILE_REPLICATION_POLICY_TO_PROJECTS_METHOD,
                                            stepId,
                                            stepDes,
                                            association.getSourceSystem(), args);
                                }
                            }
                        }
                    }
                }

            }

            String successMessage = String.format("Assigning file policy : %s, to project(s) successful.",
                    filePolicy.getId());
            workflow.executePlan(completer, successMessage);
        } catch (Exception ex) {
            // If no other resources are assigned to replication policy
            // Remove the replication topology from the policy
            FileOrchestrationUtils.removeTopologyInfo(filePolicy, s_dbClient);

            s_logger.error(String.format("Assigning file policy : %s to project(s) failed", filePolicy.getId()), ex);
            ServiceError serviceError = DeviceControllerException.errors
                    .assignFilePolicyFailed(filePolicyToAssign.toString(), filePolicy.getApplyAt(), ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void assignFilePolicyToFileSystem(FilePolicy filePolicy, List<FileDescriptor> fileDescriptors,
            String taskId) throws ControllerException {
        FileShare sourceFS = null;
        Workflow workflow = null;
        List<URI> fsURIs = FileDescriptor.getFileSystemURIs(fileDescriptors);

        FileSystemAssignPolicyWorkflowCompleter completer = new FileSystemAssignPolicyWorkflowCompleter(filePolicy.getId(), fsURIs, taskId);
        try {
            workflow = _workflowService.getNewWorkflow(this, ASSIGN_FILE_POLICY_TO_FS_WF_NAME, false, taskId);
            String waitFor = null;
            s_logger.info("Generating steps for creating mirror filesystems...");
            for (FileDescriptor fileDescriptor : fileDescriptors) {
                if (fileDescriptor.getType().toString().equals(FileDescriptor.Type.FILE_EXISTING_MIRROR_SOURCE.name())
                        || fileDescriptor.getType().toString().equals(FileDescriptor.Type.FILE_EXISTING_SOURCE.name())) {
                    sourceFS = s_dbClient.queryObject(FileShare.class, fileDescriptor.getFsURI());
                    break;
                }
            }

            // 1. If policy to be applied is of type replication and source file system doesn't have any target,
            // then we have to create mirror file system first..
            if (filePolicy.getFilePolicyType().equals(FilePolicyType.file_replication.name())) {
                waitFor = _fileDeviceController.addStepsForCheckAndCreateFileSystems(workflow, waitFor, fileDescriptors, filePolicy.getId(),
                        taskId);
                // waitFor = _fileDeviceController.addStepsForCreateFileSystems(workflow, waitFor, fileDescriptors,
                // taskId);
            }

            // 2. Apply the file protection policy
            String stepDescription = String.format("applying file policy : %s  for file system : %s",
                    filePolicy.getId(), sourceFS.getId());
            String applyFilePolicyStep = workflow.createStepId();
            Object[] args = new Object[] { sourceFS.getStorageDevice(), sourceFS.getId(), filePolicy.getId() };
            _fileDeviceController.createMethod(workflow, waitFor, APPLY_FILE_POLICY_METHOD, applyFilePolicyStep,
                    stepDescription, sourceFS.getStorageDevice(), args);

            // Finish up and execute the plan.
            String successMessage = String.format("Assigning file policy : %s, to file system: %s successful.",
                    filePolicy.getId(), sourceFS.getId());
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            s_logger.error(String.format("Assigning file policy : %s to file system : %s failed", filePolicy.getId(),
                    sourceFS.getId()), ex);
            ServiceError serviceError = DeviceControllerException.errors.assignFilePolicyFailed(filePolicy.toString(),
                    FilePolicyApplyLevel.file_system.name(), ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    private static String setVpoolLevelPolicesToCreate(Workflow workflow, VirtualPool vpool, URI storageSystem, URI nasServer,
            List<FilePolicy> filePoliciesToCreate, String waitFor) {

        StringSet fileVpoolPolicies = vpool.getFilePolicies();

        if (fileVpoolPolicies != null && !fileVpoolPolicies.isEmpty()) {
            for (String fileVpoolPolicy : fileVpoolPolicies) {
                FilePolicy filePolicy = s_dbClient.queryObject(FilePolicy.class, URIUtil.uri(fileVpoolPolicy));
                filePoliciesToCreate.add(filePolicy);
                StringSet policyStrRes = filePolicy.getPolicyStorageResources();
                if (policyStrRes != null && !policyStrRes.isEmpty()) {
                    for (String policyStrRe : policyStrRes) {
                        PolicyStorageResource strRes = s_dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(policyStrRe));
                        if (strRes.getAppliedAt().toString().equals(vpool.getId().toString())
                                && strRes.getStorageSystem().toString().equals(storageSystem.toString())
                                && strRes.getNasServer().toString().equalsIgnoreCase(nasServer.toString())) {
                            s_logger.info("File Policy {} is already exists for vpool {} , storage system {} and nas server {}",
                                    filePolicy.getFilePolicyName(), vpool.getLabel(), storageSystem.toString(), strRes);

                            /*
                             * 1. Generate file policy path
                             * 2. Check if vpool name is part of the policy path
                             * 3. If not, throw error.
                             */
                            String stepDescription = String.format("Step to check if vpool {} is part of file policy path...",
                                    vpool.getLabel());
                            String stepId = workflow.createStepId();
                            Object[] args = new Object[] { storageSystem, URIUtil.uri(fileVpoolPolicy), nasServer, vpool.getId(), null };
                            waitFor = _fileDeviceController.createMethod(workflow, waitFor,
                                    CHECK_FILE_POLICY_PATH_HAS_RESOURCE_LABEL_METHOD, stepId, stepDescription,
                                    storageSystem,
                                    args);

                            filePoliciesToCreate.remove(filePolicy);
                            break;
                        }
                    }
                }
            }
        }
        return waitFor;
    }

    private static String setAllProjectLevelPolices(Workflow workflow, Project project, VirtualPool vpool,
            URI storageSystem, URI nasServer, List<FilePolicy> filePoliciesToCreate, String waitFor) {
        StringSet fileProjectPolicies = project.getFilePolicies();

        if (fileProjectPolicies != null && !fileProjectPolicies.isEmpty()) {
            for (String fileProjectPolicy : fileProjectPolicies) {
                FilePolicy filePolicy = s_dbClient.queryObject(FilePolicy.class, URIUtil.uri(fileProjectPolicy));
                if (NullColumnValueGetter.isNullURI(filePolicy.getFilePolicyVpool())
                        || !filePolicy.getFilePolicyVpool().toString().equals(vpool.getId().toString())) {
                    continue;
                }
                filePoliciesToCreate.add(filePolicy);
                StringSet policyStrRes = filePolicy.getPolicyStorageResources();
                if (policyStrRes != null && !policyStrRes.isEmpty()) {
                    for (String policyStrRe : policyStrRes) {
                        PolicyStorageResource strRes = s_dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(policyStrRe));
                        if (strRes != null && strRes.getAppliedAt().toString().equals(project.getId().toString())
                                && strRes.getStorageSystem().toString().equals(storageSystem.toString())
                                && strRes.getNasServer().toString().equalsIgnoreCase(nasServer.toString())) {
                            s_logger.info("File Policy {} is already exists for project {} , storage system {} and nas server {}",
                                    filePolicy.getFilePolicyName(), project.getLabel(), storageSystem.toString(), strRes);

                            /*
                             * 1. Generate file policy path
                             * 2. Check if project name is part of the policy path
                             * 3. If not, throw error.
                             */
                            String stepDescription = String.format("Step to check if vpool {} is part of file policy path...",
                                    vpool.getLabel());
                            String stepId = workflow.createStepId();
                            Object[] args = new Object[] { storageSystem, URIUtil.uri(fileProjectPolicy), nasServer, vpool.getId(),
                                    project.getId() };
                            waitFor = _fileDeviceController.createMethod(workflow, waitFor,
                                    CHECK_FILE_POLICY_PATH_HAS_RESOURCE_LABEL_METHOD, stepId, stepDescription,
                                    storageSystem,
                                    args);
                            filePoliciesToCreate.remove(filePolicy);
                            break;
                        }
                    }
                }
            }
        }
        return waitFor;
    }

    /**
     * Child Workflow for failback
     * 
     * @param systemURI
     * @param fsURI source FS URI
     * @param taskId
     */
    public void doFailBackMirrorSessionWF(URI systemURI, URI fsURI, String taskId) {
        TaskCompleter taskCompleter = null;
        String stepDescription;
        String stepId;
        Object[] args;
        try {
            FileShare sourceFS = s_dbClient.queryObject(FileShare.class, fsURI);
            StorageSystem primarysystem = s_dbClient.queryObject(StorageSystem.class, systemURI);

            StringSet targets = sourceFS.getMirrorfsTargets();
            List<URI> targetFSURI = new ArrayList<>();
            for (String target : targets) {
                targetFSURI.add(URI.create(target));
            }
            FileShare targetFS = s_dbClient.queryObject(FileShare.class, targetFSURI.get(0));
            StorageSystem secondarySystem = s_dbClient.queryObject(StorageSystem.class, targetFS.getStorageDevice());

            taskCompleter = new MirrorFileFailbackTaskCompleter(FileShare.class, sourceFS.getId(), taskId);
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    FAILBACK_FILE_SYSTEM_METHOD, false, taskId, taskCompleter);

            s_logger.info("Generating steps for failback to source file share: {} from target file share: {}", fsURI, targetFS.getId());

            /*
             * Step 1. Creates a mirror replication policy for the secondary cluster i.e Resync-prep on primary cluster , this will disable
             * primary cluster replication policy.
             */
            stepDescription = String.format("source resync-prep : creating mirror policy on target system: %s", secondarySystem.getId());
            stepId = workflow.createStepId();
            args = new Object[] { primarysystem.getId(), sourceFS.getId(), "resync" };
            String waitFor = _fileDeviceController.createMethod(workflow, null, FILE_REPLICATION_OPERATIONS_METHOD, stepId,
                    stepDescription, primarysystem.getId(), args);

            /*
             * Step 2. Start the mirror replication policy manually, this will replicate new data (written during failover) from secondary
             * cluster to primary cluster.
             */
            stepDescription = String.format("start mirror policy: replicate target file share: %s, data to source file share:%s",
                    targetFS.getId(), sourceFS.getId());
            stepId = workflow.createStepId();
            args = new Object[] { secondarySystem.getId(), targetFS.getId(), "start" };
            waitFor = _fileDeviceController.createMethod(workflow, waitFor, FILE_REPLICATION_OPERATIONS_METHOD, stepId,
                    stepDescription, secondarySystem.getId(), args);

            /*
             * Step 3. Allow Write on Primary Cluster local target after replication from step 2
             * i.e Fail over to Primary Cluster
             */

            stepDescription = String.format("failover on source file system : allow write on source file share: %s", sourceFS.getId());
            stepId = workflow.createStepId();
            List<URI> combined = Arrays.asList(sourceFS.getId(), targetFS.getId());
            MirrorFileFailoverTaskCompleter failoverCompleter = new MirrorFileFailoverTaskCompleter(FileShare.class, combined, stepId);
            args = new Object[] { primarysystem.getId(), sourceFS.getId(), failoverCompleter };
            waitFor = _fileDeviceController.createMethod(workflow, waitFor, FAILOVER_FILE_SYSTEM_METHOD, stepId,
                    stepDescription, primarysystem.getId(), args);

            /*
             * Step 4. Resync-Prep on secondary cluster , same as step 1 but will be executed on secondary cluster instead of primary
             * cluster.
             */
            stepDescription = String.format(" target resync-prep : disabling mirror policy on target system: %s", secondarySystem.getId());
            stepId = workflow.createStepId();
            args = new Object[] { secondarySystem.getId(), targetFS.getId(), "resync" };
            _fileDeviceController.createMethod(workflow, waitFor, FILE_REPLICATION_OPERATIONS_METHOD, stepId,
                    stepDescription, secondarySystem.getId(), args);

            String successMsg = String.format("Failback of %s to %s successful", sourceFS.getId(), targetFS.getId());
            workflow.executePlan(taskCompleter, successMsg);

        } catch (Exception ex) {
            s_logger.error("Could not replicate source filesystem CIFS shares: " + fsURI, ex);
            String opName = ResourceOperationTypeEnum.FILE_PROTECTION_ACTION_FAILBACK.getName();
            ServiceError serviceError = DeviceControllerException.errors.createFileSharesFailed(
                    fsURI.toString(), opName, ex);
            taskCompleter.error(s_dbClient, this._locker, serviceError);
        }
    }

    /**
     * This method is responsible for reduction of fileshare quota
     * 
     * a Workflow and invoking the FileOrchestrationInterface.addStepsForReduceFileSystems
     * 
     * @param fileDescriptors
     * @param taskId
     * @throws ControllerException
     */
	@Override
	public void reduceFileSystem(List<FileDescriptor> fileDescriptors, String taskId) throws ControllerException {
	    List<URI> fileShareUris = FileDescriptor.getFileSystemURIs(fileDescriptors);
        FileWorkflowCompleter completer = new FileWorkflowCompleter(fileShareUris, taskId);
        Workflow workflow = null;
        String waitFor = null; // the wait for key returned by previous call
        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this, REDUCE_FILESYSTEMS_WF_NAME, false, taskId);
            // Next, call the FileDeviceController
            waitFor = _fileDeviceController.addStepsForReduceFileSystems(workflow, waitFor, fileDescriptors, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Reduce FileShares successful for: " + fileShareUris.toString();
            Object[] callbackArgs = new Object[] { fileShareUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not Reduce FileShares: " + fileShareUris, ex);
            releaseWorkflowLocks(workflow);
            String opName = ResourceOperationTypeEnum.REDUCE_FILE_SYSTEM.getName();
            ServiceError serviceError = DeviceControllerException.errors.reduceFileShareFailed(
            							fileShareUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
		
	}

}
