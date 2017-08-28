/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeNfsShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

public class VNXeExportFileSystemJob extends VNXeJob {
    private static final long serialVersionUID = 1L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeExportFileSystemJob.class);
    private FileShareExport exportInfo;
    private boolean isFile;
    private String shareName;

    public VNXeExportFileSystemJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter, FileShareExport export, String shareName, boolean isFile) {

        super(jobId, storageSystemUri, taskCompleter, "exportFileSystem");

        this.exportInfo = export;
        this.isFile = isFile;
        this.shareName = shareName;
    }

    /**
     * Called to update the job status when the export file system job completes.
     * 
     * @param jobContext The job context.
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {

        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);
            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, _status.name()));

            FileShare fsObj = null;
            Snapshot snapObj = null;
            URI objId = getTaskCompleter().getId();
            StorageSystem storageObj = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            if (_status == JobStatus.SUCCESS) {
                _isSuccess = true;
                FileExport newExport = exportInfo.getFileExport();
                newExport.setMountPoint(ExportUtils.getFileMountPoint(exportInfo.getStoragePort(), exportInfo.getMountPath()));
                if (isFile) {
                    fsObj = dbClient.queryObject(FileShare.class, objId);
                    updateFSExport(fsObj, dbClient, vnxeApiClient, newExport);
                } else {
                    snapObj = updateSnapExport(dbClient, vnxeApiClient, newExport);
                    fsObj = dbClient.queryObject(FileShare.class, snapObj.getParent().getURI());
                }
            } else if (_status == JobStatus.FAILED) {
                // cleanupFSExport(fsObj, dbClient);
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to export file system: %s", opId, objId.toString()));
            }
            _logger.info(logMsgBuilder.toString());
            if (isFile) {
                fsObj = dbClient.queryObject(FileShare.class, objId);
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.EXPORT_FILE_SYSTEM,
                        _isSuccess, logMsgBuilder.toString(), "", fsObj, storageObj);
            } else {
                snapObj = dbClient.queryObject(Snapshot.class, objId);
                fsObj = dbClient.queryObject(FileShare.class, snapObj.getParent().getURI());
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.EXPORT_FILE_SNAPSHOT,
                        _isSuccess, logMsgBuilder.toString(), "", snapObj, fsObj, storageObj);
            }
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeExportFIleSystemJob", e);
            setErrorStatus("Encountered an internal error during file system export job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update FileShare after exported in VNXe
     * 
     * @param fsOjb fileShare object in vipr
     * @param dbClient DbClient
     * @param vnxeApiClient VNXeApiClient
     */
    private void updateFSExport(FileShare fsObj, DbClient dbClient, VNXeApiClient apiClient, FileExport newExport) {
        _logger.info("upading file export. ");
        FSExportMap exports = fsObj.getFsExports();
        if (exports == null) {
            exports = new FSExportMap();
        }

        VNXeNfsShare nfsShare = apiClient.findNfsShare(fsObj.getNativeId(), shareName);
        String nfsShareId = nfsShare.getId();
        newExport.setIsilonId(nfsShareId);
        exports.put(newExport.getFileExportKey(), newExport);
        fsObj.setFsExports(exports);
        updateExportRules(fsObj.getId(), newExport, dbClient);
        dbClient.updateObject(fsObj);

    }

    private void updateExportRules(URI uri, FileExport fileExport, DbClient dbClient) {
        List<FileExportRule> existingRules = queryFileExports(uri, dbClient);
        FileExportRule newRule = getFileExportRule(uri, fileExport);
        if (existingRules != null && existingRules.isEmpty()) {
            newRule.setId(URIUtil.createId(FileExportRule.class));
            _logger.info("No Existing rules available for this FS Export and so creating the rule now {}", newRule);
            dbClient.createObject(newRule);
        }
        else {
            _logger.debug("Checking inside for ExitingRule(s) available for this export");
            boolean isRuleFound = false;
            for (FileExportRule rule : existingRules) {
                _logger.debug("Available Export Rule {} - Matching with New Rule {}", rule, newRule);
                if (newRule.getFsExportIndex() != null && rule.getFsExportIndex().equals(newRule.getFsExportIndex())) {
                    isRuleFound = true;
                    _logger.info("Match Found : Skipping this Rule as alreday available {}", newRule);
                    break;
                }
            }
            if (!isRuleFound) {
                _logger.info("Creating new Export Rule {}", newRule);
                newRule.setId(URIUtil.createId(FileExportRule.class));
                dbClient.createObject(newRule);
            }
        }
    }

    private FileExportRule getFileExportRule(URI uri, FileExport fileExport) {

        FileExportRule rule = new FileExportRule();
        rule.setAnon(fileExport.getRootUserMapping());
        rule.setExportPath(fileExport.getPath());
        if (!isFile)
        {
            rule.setSnapshotId(uri);

        } else {
            rule.setFileSystemId(uri);
        }
        rule.setSecFlavor(fileExport.getSecurityType());

        if (fileExport.getPermissions().equals(FileShareExport.Permissions.ro.name())
                && fileExport.getClients() != null && !fileExport.getClients().isEmpty()) {
            rule.setReadOnlyHosts(new StringSet(fileExport.getClients()));
        }
        if (fileExport.getPermissions().equals(FileShareExport.Permissions.rw.name())
                && fileExport.getClients() != null && !fileExport.getClients().isEmpty()) {
            rule.setReadWriteHosts(new StringSet(fileExport.getClients()));
        }
        if (fileExport.getPermissions().equals(FileShareExport.Permissions.root.name())
                && fileExport.getClients() != null && !fileExport.getClients().isEmpty()) {
            rule.setRootHosts(new StringSet(fileExport.getClients()));
        }
        rule.setMountPoint(fileExport.getMountPoint());
        _logger.info("Generating FileExportRule  IsilonId ? {}", fileExport.getIsilonId());
        if (fileExport.getIsilonId() != null) {
            rule.setDeviceExportId(fileExport.getIsilonId());
        }
        return rule;
    }

    private List<FileExportRule> queryFileExports(URI uri, DbClient dbClient)
    {
        List<FileExportRule> fileExportRules = null;

        try {
            ContainmentConstraint containmentConstraint;

            if (isFile) {
                _logger.info("Querying all ExportRules Using FsId {}", uri);
                containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(uri);
            } else {
                _logger.info("Querying all ExportRules Using Snapshot Id {}", uri);
                containmentConstraint = ContainmentConstraint.Factory.getSnapshotExportRulesConstraint(uri);
            }

            fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileExportRule.class,
                    containmentConstraint);

        } catch (Exception e) {
            _logger.error("Error while querying {}", e);
        }

        return fileExportRules;

    }

    /**
     * update snapshot if the export job is for snapshot export
     * 
     * @param dbClient
     * @param apiClient
     * @return Snapshot instance
     */
    private Snapshot updateSnapExport(DbClient dbClient, VNXeApiClient apiClient, FileExport newExport) {
        _logger.info("upading snap export. ");
        URI snapId = getTaskCompleter().getId();
        Snapshot snapObj = dbClient.queryObject(Snapshot.class, snapId);
        FSExportMap exports = snapObj.getFsExports();
        if (exports == null) {
            exports = new FSExportMap();
        }

        VNXeNfsShare nfsShare = apiClient.findSnapNfsShare(snapObj.getNativeId(), shareName);
        String nfsShareId = nfsShare.getId();
        newExport.setIsilonId(nfsShareId);
        exports.put(newExport.getFileExportKey(), newExport);
        snapObj.setFsExports(exports);
        updateExportRules(snapObj.getId(), newExport, dbClient);
        dbClient.updateObject(snapObj);
        return snapObj;
    }

}
