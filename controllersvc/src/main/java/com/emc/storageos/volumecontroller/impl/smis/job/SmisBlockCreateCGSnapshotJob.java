/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;

public class SmisBlockCreateCGSnapshotJob extends SmisSnapShotJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisBlockCreateCGSnapshotJob.class);
    private final Boolean _wantSyncActive;
    private final String _sourceGroupId;

    public SmisBlockCreateCGSnapshotJob(CIMObjectPath cimJob,
            URI storageSystem, boolean wantSyncActive, String sourceGroupId,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "CreateBlockCGSnapshot");
        _wantSyncActive = wantSyncActive;
        _sourceGroupId = sourceGroupId;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> syncVolumeIter = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            BlockSnapshotCreateCompleter completer = (BlockSnapshotCreateCompleter) getTaskCompleter();
            List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, completer.getSnapshotURIs());
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
            if (jobStatus == JobStatus.SUCCESS) {
                // Create mapping of volume.nativeDeviceId to BlockSnapshot object
                Map<String, BlockSnapshot> volumeToSnapMap = new HashMap<String, BlockSnapshot>();
                for (BlockSnapshot snapshot : snapshots) {
                    Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                    volumeToSnapMap.put(volume.getNativeId(), snapshot);
                }

                // Iterate through the snapshot elements that were created by the
                // Job and try to match them up with the appropriate BlockSnapshot
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                CIMObjectPath replicationGroupPath = client.associatorNames(getCimJob(), null, SmisConstants.SE_REPLICATION_GROUP, null,
                        null).next();
                String replicationGroupInstance = (String) replicationGroupPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue();
                replicationGroupInstance = SmisUtils.getTargetGroupName(replicationGroupInstance, storage.getUsingSmis80());
                String relationshipName = getRelationShipName(client, replicationGroupPath, replicationGroupInstance);
                syncVolumeIter = client.associatorNames(replicationGroupPath, null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                Calendar now = Calendar.getInstance();
                while (syncVolumeIter.hasNext()) {
                    // Get the sync volume native device id
                    CIMObjectPath syncVolumePath = syncVolumeIter.next();
                    CIMInstance syncVolume = client.getInstance(syncVolumePath, false, false, null);
                    String syncDeviceID = syncVolumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
                    String elementName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_ELEMENT_NAME);
                    // Get the associated volume for this sync volume
                    CIMObjectPath volumePath = null;
                    CloseableIterator<CIMObjectPath> volumeIter = client.associatorNames(syncVolumePath, null,
                            SmisConstants.CIM_STORAGE_VOLUME, null, null);
                    volumePath = volumeIter.next();
                    volumeIter.close();
                    String volumeDeviceID = volumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
                    String wwn = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_WWN_NAME);
                    String alternativeName =
                            CIMPropertyFactory.getPropertyValue(syncVolume,
                                    SmisConstants.CP_NAME);
                    // Lookup the associated snapshot based on the volume native device id
                    BlockSnapshot snapshot = volumeToSnapMap.get(volumeDeviceID);

                    // In the case snapping an RP+VPlex target volume, we will only be creating a single
                    // BlockSnapshot corresponding to the requested target. The corresponding backing
                    // array consistency group will still be snapped so if we have multiple target volumes,
                    // we need to perform this null check to avoid a NPE.
                    if (snapshot != null) {
                        snapshot.setNativeId(syncDeviceID);
                        snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, snapshot));
                        snapshot.setReplicationGroupInstance(replicationGroupInstance);
                        snapshot.setDeviceLabel(elementName);
                        snapshot.setInactive(false);
                        snapshot.setIsSyncActive(_wantSyncActive);
                        snapshot.setCreationTime(now);
                        snapshot.setWWN(wwn.toUpperCase());
                        snapshot.setAlternateName(alternativeName);
                        commonSnapshotUpdate(snapshot, syncVolume, client, storage, _sourceGroupId, relationshipName, true, dbClient);
                        _log.info(String.format("For sync volume path %1$s, going to set blocksnapshot %2$s nativeId to %3$s (%4$s). " +
                                "Replication Group instance is %5$s. Associated volume is %6$s",
                                syncVolumePath.toString(), snapshot.getId().toString(),
                                syncDeviceID, elementName, replicationGroupInstance,
                                volumePath.toString()));
                        dbClient.updateObject(snapshot);
                        getTaskCompleter().ready(dbClient);
                    }
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to create snapshot");
                for (BlockSnapshot snapshot : snapshots) {
                    snapshot.setInactive(true);
                }
                dbClient.persistObject(snapshots);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during create CG snapshot job status processing: " + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisBlockCreateCGSnapshotJob", e);
        } finally {
            if (syncVolumeIter != null) {
                syncVolumeIter.close();
            }
            super.updateStatus(jobContext);
        }
    }

    private String getRelationShipName(WBEMClient client, CIMObjectPath replicationGroupPath,
            String replicationGroupID) {
        CloseableIterator<CIMInstance> iterator = null;
        try {
            _log.info("replicationGroupID : {}", replicationGroupID);
            iterator = client.referenceInstances(replicationGroupPath,
                    SmisConstants.SE_GROUP_SYNCHRONIZED_RG_RG, null, false, null);
            while (iterator.hasNext()) {
                CIMInstance groupSynchronized = iterator.next();
                CIMProperty syncElement = groupSynchronized.getProperty(SmisConstants.CP_SYNCED_ELEMENT);
                _log.info("syncElement : {}", syncElement.getValue().toString());
                if (syncElement.toString().contains(replicationGroupID)) {
                    String relationshipName = (String) groupSynchronized
                            .getProperty(SmisConstants.RELATIONSHIP_NAME).getValue();
                    _log.info("Relationship name : {}", relationshipName);
                    return relationshipName;
                }

            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to get the relationship name for Group Synchronized");
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
        return null;
    }

}
