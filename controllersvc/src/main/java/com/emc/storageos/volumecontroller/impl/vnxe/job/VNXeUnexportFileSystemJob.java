/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

public class VNXeUnexportFileSystemJob extends VNXeJob {
    private static final long serialVersionUID = 1L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeUnexportFileSystemJob.class);
    private FileShareExport exportInfo;
    private boolean isFile;
    private String exportPath;
    private static final String SECURITY_FLAVOR = "sys";

    public VNXeUnexportFileSystemJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter, FileShareExport export, String exportPath, boolean isFile) {

        super(jobId, storageSystemUri, taskCompleter, "unexportFileSystem");

        this.exportInfo = export;
        this.exportPath = exportPath;
        this.isFile = isFile;
    }

    /**
     * Called to update the job status when the unexport file system job completes.
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
            StorageSystem storageObj = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            if (_status == JobStatus.SUCCESS) {
                _isSuccess = true;
                if (isFile) {
                    URI fsId = getTaskCompleter().getId();
                    fsObj = dbClient.queryObject(FileShare.class, fsId);
                    FileExportRule rule = getFileExportRule(fsId);
                    updateExportRule(dbClient, rule);
                    updateFSExport(fsObj, dbClient, vnxeApiClient);
                } else {
                    URI snapshotId = getTaskCompleter().getId();
                    FileExportRule rule = getFileExportRule(snapshotId);
                    updateExportRule(dbClient, rule);
                    snapObj = updateSnapExport(dbClient, vnxeApiClient);
                    fsObj = dbClient.queryObject(FileShare.class, snapObj.getParent());
                }
            } else {
                // cleanupFSExport(fsObj, dbClient);
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to export file system", opId));
            }
            _logger.info(logMsgBuilder.toString());
            if (isFile) {
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.UNEXPORT_FILE_SYSTEM, _isSuccess,
                        logMsgBuilder.toString(), "", fsObj, storageObj);
            } else {
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.UNEXPORT_FILE_SNAPSHOT, _isSuccess,
                        logMsgBuilder.toString(), "", snapObj, fsObj, storageObj);
            }
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeUnexportFIleSystemJob", e);
            setErrorStatus("Encountered an internal error during file system unexport job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update FileShare after unexport finished in vnxe
     * 
     * @param fsOjb fileShare object in vipr
     * @param dbClient DbClient
     * @param vnxeApiClient VNXeApiClient
     */
    private void updateFSExport(FileShare fsObj, DbClient dbClient, VNXeApiClient apiClient) {
        _logger.info("upading file export. ");
        FSExportMap exports = fsObj.getFsExports();
        if (exports == null) {
            _logger.info("No exports found in the file system. ");
            return;
        }
        if (exportInfo != null) {
            FileExport export = exportInfo.getFileExport();
            FileExport existExport = exports.get(export.getFileExportKey());
            if (existExport != null) {
                exports.remove(existExport.getFileExportKey());
                dbClient.persistObject(fsObj);
            }
        }
    }

    private Snapshot updateSnapExport(DbClient dbClient, VNXeApiClient apiClient) {
        URI snapId = getTaskCompleter().getId();
        Snapshot snapObj = dbClient.queryObject(Snapshot.class, snapId);
        FSExportMap exports = snapObj.getFsExports();
        if (exports == null) {
            _logger.info("No exports found in the file system. ");
            return snapObj;
        }
        if (exportInfo != null) {
            FileExport export = exportInfo.getFileExport();
            FileExport existExport = exports.get(export.getFileExportKey());
            if (existExport != null) {
                exports.remove(existExport.getFileExportKey());
                dbClient.persistObject(snapObj);
            }
        }
        return snapObj;
    }

    private void updateExportRule(DbClient dbClient, FileExportRule rule) {
        // Query Existing Export rule and if found set to delete.

        URIQueryResultList dbresult = new URIQueryResultList();

        if (!isFile && rule.getSnapshotExportIndex() != null) {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotExportRuleConstraint(rule.getSnapshotExportIndex()), dbresult);
        } else if (rule.getFsExportIndex() != null) {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileExportRuleConstraint(rule.getFsExportIndex()), dbresult);
        }

        Iterator<URI> it = dbresult.iterator();
        while (it.hasNext()) {
            if (dbresult.iterator().hasNext()) {
                rule = dbClient.queryObject(FileExportRule.class, it.next());
                if (rule != null && !rule.getInactive()) {
                    _logger.info("Existing DB Model found {}", rule);
                    rule.setInactive(true);
                    dbClient.persistObject(rule);
                    break;
                }
            }
        }
    }

    private FileExportRule getFileExportRule(URI uri) {

        FileExportRule rule = new FileExportRule();
        rule.setExportPath(exportPath);
        rule.setSecFlavor(SECURITY_FLAVOR);
        if (!isFile)
        {
            rule.setSnapshotId(uri);
        } else {
            rule.setFileSystemId(uri);
        }
        return rule;
    }
}
