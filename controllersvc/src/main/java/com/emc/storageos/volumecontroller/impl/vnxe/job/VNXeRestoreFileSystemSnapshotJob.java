/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeFileSystemSnap;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class VNXeRestoreFileSystemSnapshotJob extends VNXeJob {

    private static final long serialVersionUID = 154563020105138725L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeCreateFileSystemSnapshotJob.class);

    public VNXeRestoreFileSystemSnapshotJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "restoreFileSystemSnapshot");
    }

    /**
     * Called to update the job status when the file system snapshot restore job completes.
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
            URI snapId = getTaskCompleter().getId();
            Snapshot snapshotObj = dbClient.queryObject(Snapshot.class, snapId);
            URI fsUri = snapshotObj.getParent().getURI();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsUri);
            String event = null;
            if (_status == JobStatus.SUCCESS && snapshotObj != null) {
                syncSnapshots(dbClient, fsObj, vnxeApiClient);
                event = String.format(
                        "Restore file system snapshot successfully for URI: %s", getTaskCompleter().getId());
            } else if (_status == JobStatus.FAILED && snapshotObj != null) {
                event = String.format(
                        "Task %s failed to restore file system snapshot: %s", opId, snapshotObj.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);

            } else {
                logMsgBuilder.append(String.format("Could not find the snapshot:%s", snapId.toString()));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.RESTORE_FILE_SNAPSHOT, _isSuccess,
                    event, "", fsObj, snapId);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeRestoreFileSystemSnapshotJob", e);
            setErrorStatus("Encountered an internal error during file system snapshot restore job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    private void syncSnapshots(DbClient dbClient, FileShare fsObj,
            VNXeApiClient vnxeApiClient) {
        // Retrieve all snapshots from DB that belong to this file system
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.
                Factory.getFileshareSnapshotConstraint(fsObj.getId()), results);

        // Setup snapshot name-object map
        Map<String, Snapshot> snapshotsInDB = new ConcurrentHashMap<String, Snapshot>();
        while (results.iterator().hasNext()) {
            URI uri = results.iterator().next();
            Snapshot snap = dbClient.queryObject(Snapshot.class, uri);
            String nativeId = snap.getNativeId();
            if (nativeId == null || nativeId.isEmpty()) {
                // no nativeId set in the snap, remove it from db.
                snap.setInactive(true);
                dbClient.persistObject(snap);
                _logger.info("No nativeId, removing the snapshot: {}", snap.getId());
                continue;
            } else {
                snapshotsInDB.put(nativeId, snap);
            }
        }

        // Retrieve list of valid snapshot names from the device
        List<VNXeFileSystemSnap> snapshots = vnxeApiClient.getFileSystemSnaps(fsObj.getNativeId());
        List<String> snapIdsOnDevice = new ArrayList<String>();
        for (VNXeFileSystemSnap snap : snapshots) {
            snapIdsOnDevice.add(snap.getId());
        }

        // Iterate through the snapshots in the DB and if name not found in
        // the list returned by the device, mark snapshot in DB as inactive
        Set<String> snapshotNativeIds = snapshotsInDB.keySet();
        for (String snapshotId : snapshotNativeIds) {
            if (!snapIdsOnDevice.contains(snapshotId)) {
                _logger.info("Removing the snapshot: {}", snapshotId);
                snapshotsInDB.get(snapshotId).setInactive(true);
                dbClient.persistObject(snapshotsInDB.get(snapshotId));
            }
        }

        // Iterate through the snapshot list from device and if a
        // snapshot is found on the device but not in the DB, add the
        // newly discovered snapshot to the DB.
        for (VNXeFileSystemSnap snap : snapshots) {
            if (!snapshotNativeIds.contains(snap.getId())) {
                _logger.info("adding the snapshot: {}", snap.getId());
                Snapshot newSnap = new Snapshot();
                newSnap.setCreationTime(Calendar.getInstance());
                newSnap.setId(URIUtil.createId(Snapshot.class));
                newSnap.setParent(new NamedURI(fsObj.getId(), fsObj.getLabel()));
                newSnap.setLabel(snap.getName());
                newSnap.setOpStatus(new OpStatusMap());
                newSnap.setProject(new NamedURI(fsObj.getProject().getURI(), fsObj.getProject().getName()));
                newSnap.setNativeId(snap.getId());
                try {
                    newSnap.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, newSnap));
                } catch (IOException e) {
                    _logger.info("Exception while setting snap's nativeGUID");
                }
            }
        }
    }

}
