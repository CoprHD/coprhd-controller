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
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

public class VNXeDeleteFileSystemJob extends VNXeJob {
    private static final long serialVersionUID = 1L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeCreateFileSystemJob.class);
    private boolean isForceDelete = false;

    public VNXeDeleteFileSystemJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter, boolean forceDelete) {
        super(jobId, storageSystemUri, taskCompleter, "deleteFileSystem");
        isForceDelete = forceDelete;
    }

    /**
     * Called to update the job status when the file system delete job completes.
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

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, _status.name()));

            VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);
            StorageSystem storageObj = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            URI fsId = getTaskCompleter().getId();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsId);
            // If terminal state update storage pool capacity and remove reservation for volumes capacity
            // from pool's reserved capacity map.
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                if (fsObj != null) {
                    VNXeJob.updateStoragePoolCapacity(dbClient, vnxeApiClient, fsObj.getPool(), null);
                }
            }
            if (_status == JobStatus.SUCCESS && fsObj != null) {
                if (isForceDelete) {
                    updateSnapshots(dbClient, fsObj);
                }
                fsObj.setInactive(true);
                dbClient.persistObject(fsObj);
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s succeeded to delete file system: %s", opId, fsId.toString()));

            } else if (_status == JobStatus.FAILED && fsObj != null) {

                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to delete file system: %s", opId, fsId.toString()));
                fsObj.setInactive(false);
                dbClient.persistObject(fsObj);

            } else {
                logMsgBuilder.append(String.format("The file system: %s is not found anymore", fsId));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM, _isSuccess, "", "", fsObj,
                    storageObj);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeDeleteFileSystemJob", e);
            setErrorStatus("Encountered an internal error during file system delete job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    private void updateSnapshots(DbClient dbClient, FileShare fsObj) {
        _logger.info(" Setting Snapshots to InActive with Force Delete ");
        URIQueryResultList snapIDList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getFileshareSnapshotConstraint(fsObj.getId()), snapIDList);
        if (!snapIDList.isEmpty()) {
            List<URI> idList = new ArrayList<URI>();
            for (Iterator<URI> iter = snapIDList.iterator(); iter.hasNext();) {
                URI id = iter.next();
                idList.add(id);
            }
            List<Snapshot> snapList = dbClient.queryObject(
                    Snapshot.class, snapIDList);
            for (Snapshot snapshot : snapList) {
                _logger.info("Marking Snapshot as InActive Snapshot Id {} Fs Id : {}", snapshot.getId(), snapshot.getParent());
                snapshot.setInactive(true);
                dbClient.persistObject(snapshot);
            }

        }
    }
}
