/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

public class VNXeDeleteShareJob extends VNXeJob {

    private static final long serialVersionUID = 8280707128357602535L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeDeleteShareJob.class);
    private FileSMBShare smbShare;
    private boolean isFile;

    public VNXeDeleteShareJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter, FileSMBShare smbShare, boolean isFile) {
        super(jobId, storageSystemUri, taskCompleter, "deleteSMBShare");
        this.smbShare = smbShare;
        this.isFile = isFile;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {

        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, _status.name()));

            VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);

            URI fsId = getTaskCompleter().getId();

            FileShare fsObj = null;
            Snapshot snapObj = null;
            String event = null;
            if (_status == JobStatus.SUCCESS) {
                if (isFile) {
                    fsObj = dbClient.queryObject(FileShare.class, fsId);
                    updateFileSystem(vnxeApiClient, dbClient, fsObj);
                } else {
                    snapObj = updateSnapshot(vnxeApiClient, dbClient);
                    fsObj = dbClient.queryObject(FileShare.class, snapObj.getParent());
                }
                event = String.format(
                        "Deleted file system smbShare successfully for URI: %s", getTaskCompleter().getId());
            } else if (_status == JobStatus.FAILED) {
                event = String.format(
                        "Task %s failed to delete file system sbmShare: %s", opId, smbShare.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);

            }
            _logger.info(logMsgBuilder.toString());
            if (isFile) {
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM_SHARE, _isSuccess,
                        event, smbShare.getName(), fsObj, smbShare);
            } else {
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE, _isSuccess,
                        event, smbShare.getName(), snapObj, fsObj, smbShare);
            }
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeCreateFileSystemSnapshotJob", e);
            setErrorStatus("Encountered an internal error during file system snapshot create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update file system object with the SMB share.
     * 
     * @param apiClient
     * @param dbClient
     * @param fsObj
     */
    private void updateFileSystem(VNXeApiClient apiClient, DbClient dbClient, FileShare fsObj) {
        SMBShareMap shareMap = fsObj.getSMBFileShares();
        if (shareMap == null) {
            return;
        }
        shareMap.remove(smbShare.getName());
        deleteShareACLsFromDB(dbClient, fsObj);
        dbClient.persistObject(fsObj);
    }

    private Snapshot updateSnapshot(VNXeApiClient apiClient, DbClient dbClient) {
        URI snapId = getTaskCompleter().getId();
        Snapshot snapObj = dbClient.queryObject(Snapshot.class, snapId);
        SMBShareMap shareMap = snapObj.getSMBFileShares();
        if (shareMap == null) {
            return snapObj;
        }
        shareMap.remove(smbShare.getName());
        deleteShareACLsFromDB(dbClient, snapObj);
        dbClient.persistObject(snapObj);
        return snapObj;
    }

    private void deleteShareACLsFromDB(DbClient dbClient, FileObject fsObj) {

        try {

            ContainmentConstraint containmentConstraint = null;
            if (isFile && fsObj != null) {
                _logger.info("Querying DB for Share ACLs of share {} of filesystemId {} ", smbShare.getName(), fsObj.getId());
                containmentConstraint = ContainmentConstraint.Factory.getFileCifsShareAclsConstraint(fsObj.getId());
            } else if (!isFile && fsObj != null) {
                URI snapshotId = fsObj.getId();
                _logger.info("Querying DB for Share ACLs of share {} of SnapshotId {} ", smbShare.getName(), fsObj.getId());
                containmentConstraint = ContainmentConstraint.Factory.getSnapshotCifsShareAclsConstraint(snapshotId);
            }

            List<CifsShareACL> shareAclList = CustomQueryUtility.queryActiveResourcesByConstraint(
                    dbClient, CifsShareACL.class, containmentConstraint);
            List<CifsShareACL> deleteAclList = new ArrayList<CifsShareACL>();

            if (!shareAclList.isEmpty()) {
                Iterator<CifsShareACL> shareAclIter = shareAclList.iterator();
                while (shareAclIter.hasNext()) {
                    CifsShareACL shareAcl = shareAclIter.next();
                    if (smbShare.getName().equals(shareAcl.getShareName())) {
                        shareAcl.setInactive(true);
                        deleteAclList.add(shareAcl);
                    }
                }
                if (!deleteAclList.isEmpty()) {
                    _logger.info("Deleting ACLs of share {} of filesystem {}", smbShare.getName(), fsObj.getLabel());
                    dbClient.persistObject(deleteAclList);
                }
            }
        } catch (Exception e) {
            _logger.error("Error while querying DB for ACL(s) of a share {}", e);
        }
    }

}
