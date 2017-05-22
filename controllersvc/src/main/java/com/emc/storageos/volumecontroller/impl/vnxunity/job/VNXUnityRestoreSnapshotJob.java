/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.ParametersOut;
import com.emc.storageos.vnxe.models.Snap;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeJob;

public class VNXUnityRestoreSnapshotJob extends VNXeJob {
    /**
     * 
     */
    private static final long serialVersionUID = 5317802800817893517L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXUnityRestoreSnapshotJob.class);

    public VNXUnityRestoreSnapshotJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "RestoreSnapshot");
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
            URI projectUri = snapshotObj.getProject().getURI();
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            if (_status == JobStatus.SUCCESS && snapshotObj != null) {
                VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);
                VNXeCommandJob vnxeJob = vnxeApiClient.getJob(getJobIds().get(0));
                ParametersOut output = vnxeJob.getParametersOut();
                // get the id of the backup snapshot created before restore operation
                String backUpSnapId = output.getBackup().getId();
                Snap backupSnap = vnxeApiClient.getSnapshot(backUpSnapId);
                if (NullColumnValueGetter.isNotNullValue(snapshotObj.getReplicationGroupInstance())) {
                    List<BlockSnapshot> snapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshotObj, dbClient);
                    // Create a snapshot corresponding to the backup snap for each volume in the consistency group
                    final List<BlockSnapshot> snapshotList = new ArrayList<BlockSnapshot>();
                    Map<String, BlockSnapshot> volumeToSnapMap = new HashMap<String, BlockSnapshot>();
                    int count = 1;
                    String setLabel = backupSnap.getName();
                    for (BlockSnapshot snapshot : snapshots) {
                        BlockObject parent = BlockObject.fetch(dbClient, snapshot.getParent().getURI());
                        
                        String label = String.format("%s-%s", setLabel, count++);
                        final BlockSnapshot newSnap = initSnapshot(parent, label, setLabel, projectUri);
                        newSnap.setOpStatus(new OpStatusMap());
                        snapshotList.add(newSnap);
                        volumeToSnapMap.put(parent.getNativeId(), newSnap);
                    }
                    List<Snap> snaps = vnxeApiClient.getSnapshotsBySnapGroup(backUpSnapId);
                    for (Snap snap : snaps) {
                        String lunId = snap.getLun().getId();
                        BlockSnapshot snapshot = volumeToSnapMap.get(lunId);
                        snapshot.setReplicationGroupInstance(backUpSnapId);
                        createSnapshot(snapshot, snap, storage, dbClient);
                    }
                } else {
                    Volume vol = dbClient.queryObject(Volume.class, snapshotObj.getParent());
                    final BlockSnapshot newSnap = initSnapshot(vol, backupSnap.getName(), backupSnap.getName(), projectUri);
                    createSnapshot(newSnap, backupSnap, storage, dbClient);
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

    /**
     * Create the BlockSnapshot in DB
     * @param snapshot The snapshot to be created
     * @param unitySnap The VNX Unity snap instance
     * @param storage The storage system
     * @param dbClient 
     */
    private void createSnapshot(BlockSnapshot snapshot, Snap unitySnap, StorageSystem storage, DbClient dbClient) {
        snapshot.setNativeId(unitySnap.getId());
        snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, snapshot));
        snapshot.setDeviceLabel(unitySnap.getName());
        snapshot.setIsSyncActive(true);
        snapshot.setInactive(false);
        snapshot.setCreationTime(Calendar.getInstance());
        snapshot.setWWN(unitySnap.getAttachedWWN());
        snapshot.setAllocatedCapacity(unitySnap.getSize());
        snapshot.setProvisionedCapacity(unitySnap.getSize());
        dbClient.createObject(snapshot);
    }
    
    /**
     * Initiate a BlockSnapshot based on its parent
     * @param blockObj The parent of the BlockSnapshot
     * @param label The snapshot label
     * @param setLabel The set of snapshots label
     * @return
     */
    private BlockSnapshot initSnapshot(final BlockObject blockObj, final String label, final String setLabel, 
            final URI projectUri) {
        BlockSnapshot createdSnap = new BlockSnapshot();
        createdSnap.setId(URIUtil.createId(BlockSnapshot.class));
        createdSnap.setConsistencyGroup(blockObj.getConsistencyGroup());
        createdSnap.setSourceNativeId(blockObj.getNativeId());
        createdSnap.setParent(new NamedURI(blockObj.getId(), label));
        createdSnap.setLabel(label);
        createdSnap.setStorageController(blockObj.getStorageController());
        createdSnap.setSystemType(blockObj.getSystemType());
        createdSnap.setVirtualArray(blockObj.getVirtualArray());
        createdSnap.setProtocol(new StringSet());
        createdSnap.getProtocol().addAll(blockObj.getProtocol());
        createdSnap.setTechnologyType(TechnologyType.NATIVE.name());
        if (blockObj instanceof Volume ) {
            createdSnap.setProject(new NamedURI(projectUri, label));
        } else if (blockObj instanceof BlockSnapshot) {
            createdSnap.setProject(new NamedURI(projectUri, label));
        }
        createdSnap.setSnapsetLabel(ResourceOnlyNameGenerator.removeSpecialCharsForName(setLabel,
                SmisConstants.MAX_SNAPSHOT_NAME_LENGTH));
        return createdSnap;

    }
}
