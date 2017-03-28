/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.ParametersOut;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.vnxe.models.VNXeLunGroupSnap;
import com.emc.storageos.vnxe.models.VNXeLunSnap;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class VNXeBlockRestoreSnapshotJob extends VNXeJob {

    private static final long serialVersionUID = -1641333012646703948L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeBlockRestoreSnapshotJob.class);

    public VNXeBlockRestoreSnapshotJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "restoreSnapshot");
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            String opId = getTaskCompleter().getOpId();
            _logger.info(String.format("Updating status of job %s to %s", opId, _status.name()));

            URI snapId = getTaskCompleter().getId();
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapId);
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            if (_status == JobStatus.SUCCESS && snapshotObj != null) {
                VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);
                VNXeCommandJob vnxeJob = vnxeApiClient.getJob(getJobIds().get(0));
                ParametersOut output = vnxeJob.getParametersOut();
                // get the id of the backup snapshot created before restore operation
                String backUpSnapId = output.getBackup().getId();
                if (snapshotObj.getConsistencyGroup() != null) {
                    VNXeLunGroupSnap backupSnap = vnxeApiClient.getLunGroupSnapshot(backUpSnapId);
                    List<VNXeLun> groupLuns = vnxeApiClient.getLunByStorageResourceId(backupSnap.getStorageResource().getId());

                    // Create a snapshot corresponding to the backup snap for each volume in the consistency group
                    URI cgID = snapshotObj.getConsistencyGroup();
                    List<Volume> cgVolumes = getCGVolumes(cgID.toString(), dbClient);
                    final List<BlockSnapshot> snapshotList = new ArrayList<BlockSnapshot>();
                    Map<String, BlockSnapshot> volumeToSnapMap = new HashMap<String, BlockSnapshot>();
                    for (Volume vol : cgVolumes) {
                        final BlockSnapshot newSnap = getSnapshotFromVol(vol, backupSnap.getName());
                        newSnap.setOpStatus(new OpStatusMap());
                        snapshotList.add(newSnap);
                        volumeToSnapMap.put(vol.getNativeId(), newSnap);
                    }

                    // Add vnxe information to the new snapshots
                    for (VNXeLun groupLun : groupLuns) {
                        BlockSnapshot snapshot = volumeToSnapMap.get(groupLun.getId());
                        if (snapshot == null) {
                            _logger.info("No snapshot found for the vnxe lun - ", groupLun.getId());
                            continue;
                        }

                        snapshot.setNativeId(backUpSnapId);
                        snapshot.setReplicationGroupInstance(backUpSnapId);
                        processSnapshot(snapshot, storage, groupLun, dbClient);
                    }

                    dbClient.createObject(snapshotList);

                } else {
                    VNXeLunSnap backupSnap = vnxeApiClient.getLunSnapshot(backUpSnapId);
                    VNXeLun lun = vnxeApiClient.getLun(backupSnap.getLun().getId());
                    Volume vol = dbClient.queryObject(Volume.class, snapshotObj.getParent());
                    final BlockSnapshot newSnap = getSnapshotFromVol(vol, backupSnap.getName());
                    newSnap.setNativeId(backUpSnapId);
                    processSnapshot(newSnap, storage, lun, dbClient);
                }

                getTaskCompleter().ready(dbClient);
            } else if (_status == JobStatus.FAILED && snapshotObj != null) {
                _logger.info(String.format(
                        "Task %s failed to restore volume snapshot: %s", opId, snapshotObj.getLabel()));
            }

        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeBlockRestoreSnapshotJob", e);
            setErrorStatus("Encountered an internal error during snapshot restore job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    private BlockSnapshot getSnapshotFromVol(final Volume volume, final String label) {
        BlockSnapshot createdSnap = new BlockSnapshot();
        createdSnap.setId(URIUtil.createId(BlockSnapshot.class));
        createdSnap.setConsistencyGroup(volume.getConsistencyGroup());
        createdSnap.setSourceNativeId(volume.getNativeId());
        createdSnap.setParent(new NamedURI(volume.getId(), label));
        createdSnap.setLabel(label);
        createdSnap.setStorageController(volume.getStorageController());
        createdSnap.setSystemType(volume.getSystemType());
        createdSnap.setVirtualArray(volume.getVirtualArray());
        createdSnap.setProtocol(new StringSet());
        createdSnap.getProtocol().addAll(volume.getProtocol());
        createdSnap.setProject(new NamedURI(volume.getProject().getURI(), label));
        createdSnap.setSnapsetLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(label,
                SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
        return createdSnap;

    }

    private void processSnapshot(BlockSnapshot snapshot, StorageSystem storage, VNXeLun lun, DbClient dbClient) {
        snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, snapshot));
        snapshot.setDeviceLabel(lun.getName());
        snapshot.setIsSyncActive(true);
        snapshot.setInactive(false);
        snapshot.setCreationTime(Calendar.getInstance());
        snapshot.setWWN(lun.getSnapWwn());
        snapshot.setAllocatedCapacity(lun.getSnapsSizeAllocated());
        snapshot.setProvisionedCapacity(lun.getSnapsSize());
        _logger.info(String.format("Going to set blocksnapshot %1$s nativeId to %2$s (%3$s). Associated lun is %4$s (%5$s)",
                snapshot.getId().toString(), lun.getStorageResource().getId(), snapshot.getLabel(), lun.getId(), lun.getName()));
    }

    private List<Volume> getCGVolumes(String cgID, DbClient dbClient) {
        List<Volume> volumes = new ArrayList<Volume>();
        final URIQueryResultList uriQueryResultList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getBlockObjectsByConsistencyGroup(cgID),
                uriQueryResultList);
        Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList);
        while (volumeIterator.hasNext()) {
            Volume next = volumeIterator.next();

            if (!next.getInactive()) {
                volumes.add(next);
            }
        }

        return volumes;
    }

}
