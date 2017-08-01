/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeCifsShare;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

/**
 * This is the class for create share (file and snapshot) job
 * 
 */
public class VNXeCreateShareJob extends VNXeJob {

    private static final long serialVersionUID = 2842416097631512608L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeCreateShareJob.class);
    private FileSMBShare smbShare;
    private boolean isFile;

    public VNXeCreateShareJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter, FileSMBShare smbShare, boolean isFile) {
        super(jobId, storageSystemUri, taskCompleter, "createSMBShare");
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

            String event = "";
            FileShare fsObj = null;
            Snapshot snapObj = null;

            URI fsId = getTaskCompleter().getId();
            fsObj = dbClient.queryObject(FileShare.class, fsId);

            if (_status == JobStatus.SUCCESS) {
                if (isFile) {

                    updateFileSystem(vnxeApiClient, dbClient, fsObj);
                    event = String.format(
                            "Create file system smbShare successfully for URI: %s", getTaskCompleter().getId());
                } else {
                    snapObj = updateSnapshot(vnxeApiClient, dbClient);
                    event = String.format(
                            "Create snapshot smbShare successfully for URI: %s", getTaskCompleter().getId());
                }
            } else if (_status == JobStatus.FAILED) {
                event = String.format(
                        "Task %s failed to create file system smbShare: %s", opId, smbShare.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);

            }
            _logger.info(logMsgBuilder.toString());
            if (isFile) {
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_SHARE, _isSuccess,
                        event, smbShare.getName(), fsObj, smbShare);
            } else {
                fsObj = dbClient.queryObject(FileShare.class, snapObj.getParent());

                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.CREATE_FILE_SNAPSHOT_SHARE, _isSuccess,
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
            shareMap = new SMBShareMap();
            fsObj.setSMBFileShares(shareMap);
        }
        VNXeCifsShare vnxeShare = apiClient.findCifsShareByName(smbShare.getName());
        if (vnxeShare != null) {
            smbShare.setNativeId(vnxeShare.getId());
            SMBFileShare share = smbShare.getSMBFileShare();
            // set Mount Point
            share.setMountPoint(share.getNetBIOSName(), share.getStoragePortNetworkId(), share.getStoragePortName(), share.getName());
            shareMap.put(share.getName(), share);
            createDefaultACEForShare(dbClient, fsObj.getId(), smbShare);
        } else {
            _logger.error("Could not find the smbShare : {} in vnxe", smbShare.getName());
            setErrorStatus(String.format("Could not find the smbShare: %s in the VNXe array", smbShare.getName()));
        }
        dbClient.updateObject(fsObj);
    }

    private Snapshot updateSnapshot(VNXeApiClient apiClient, DbClient dbClient) {
        URI snapId = getTaskCompleter().getId();
        Snapshot snapObj = dbClient.queryObject(Snapshot.class, snapId);

        SMBShareMap shareMap = snapObj.getSMBFileShares();
        if (shareMap == null) {
            shareMap = new SMBShareMap();
            snapObj.setSMBFileShares(shareMap);
        }
        VNXeCifsShare vnxeShare = apiClient.findCifsShareByName(smbShare.getName());
        if (vnxeShare != null) {
            smbShare.setNativeId(vnxeShare.getId());
            SMBFileShare share = smbShare.getSMBFileShare();
            // set Mount Point
            share.setMountPoint(share.getNetBIOSName(), share.getStoragePortNetworkId(), share.getStoragePortName(), share.getName());
            shareMap.put(share.getName(), share);
            createDefaultACEForShare(dbClient, snapId, smbShare);
        } else {
            _logger.error("Could not find the smbShare : {} in vnxe", smbShare.getName());
            setErrorStatus(String.format("Could not find the smbShare: %s in the VNXe array", smbShare.getName()));
        }
        dbClient.updateObject(snapObj);
        return snapObj;
    }

    private void createDefaultACEForShare(DbClient dbClient, URI id, FileSMBShare fileShare) {

        SMBFileShare share = fileShare.getSMBFileShare();
        CifsShareACL ace = new CifsShareACL();
        ace.setUser(FileControllerConstants.CIFS_SHARE_USER_EVERYONE);
        String permission = null;
        switch (share.getPermission()) {
            case "read":
                permission = FileControllerConstants.CIFS_SHARE_PERMISSION_READ;
                break;
            case "change":
                permission = FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE;
                break;
            case "full":
                permission = FileControllerConstants.CIFS_SHARE_PERMISSION_FULLCONTROL;
                break;
        }
        ace.setPermission(permission);
        ace.setId(URIUtil.createId(CifsShareACL.class));
        ace.setShareName(share.getName());
        if (URIUtil.isType(id, FileShare.class)) {
            ace.setFileSystemId(id);
        } else {
            ace.setSnapshotId(id);
        }

        _logger.info("Creating default ACE for the share: {}", ace);
        dbClient.createObject(ace);
    }

}
