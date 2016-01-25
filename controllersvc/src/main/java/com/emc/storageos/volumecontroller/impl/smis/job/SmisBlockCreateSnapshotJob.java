/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.Calendar;
import java.util.List;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
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

public class SmisBlockCreateSnapshotJob extends SmisSnapShotJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisBlockCreateSnapshotJob.class);
    Boolean _wantSyncActive;

    public SmisBlockCreateSnapshotJob(CIMObjectPath cimJob,
            URI storageSystem, boolean wantSyncActive,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "CreateBlockSnapshot");
        _wantSyncActive = wantSyncActive;
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
                _log.info(String.format(
                        "Post-processing successful snap creation task:%s. Expected: snapshot.size() = 1; Actual: snapshots.size() = %d",
                        getTaskCompleter().getOpId(), snapshots.size()));
                // Get the snapshot device ID and set it against the BlockSnapshot object
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                syncVolumeIter = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                if (syncVolumeIter.hasNext()) {
                    // Get the sync volume native device id
                    CIMObjectPath syncVolumePath = syncVolumeIter.next();
                    CIMInstance syncVolume = client.getInstance(syncVolumePath, false, false, null);
                    String syncDeviceID = syncVolumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
                    String elementName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_ELEMENT_NAME);
                    String wwn = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_WWN_NAME);
                    String alternateName =
                            CIMPropertyFactory.getPropertyValue(syncVolume,
                                    SmisConstants.CP_NAME);
                    // Lookup the associated snapshot based on the volume native device id
                    BlockSnapshot snapshot = snapshots.get(0);
                    Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
                    snapshot.setNativeId(syncDeviceID);
                    snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, snapshot));
                    snapshot.setDeviceLabel(elementName);
                    snapshot.setInactive(false);
                    snapshot.setIsSyncActive(_wantSyncActive);
                    snapshot.setCreationTime(Calendar.getInstance());
                    snapshot.setWWN(wwn.toUpperCase());
                    snapshot.setAlternateName(alternateName);
                    commonSnapshotUpdate(snapshot, syncVolume, client, storage, volume.getNativeId(), syncDeviceID, true, dbClient);
                    _log.info(String
                            .format("For sync volume path %1$s, going to set blocksnapshot %2$s nativeId to %3$s (%4$s). Associated volume is %5$s (%6$s)",
                                    syncVolumePath.toString(), snapshot.getId().toString(),
                                    syncDeviceID, elementName, volume.getNativeId(), volume.getDeviceLabel()));
                    dbClient.persistObject(snapshot);
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to create snapshot");
                BlockSnapshot snapshot = snapshots.get(0);
                snapshot.setInactive(true);
                dbClient.persistObject(snapshot);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during block create snapshot job status processing: "
                    + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisBlockCreateSnapshotJob", e);
        } finally {
            if (syncVolumeIter != null) {
                syncVolumeIter.close();
            }
            super.updateStatus(jobContext);
        }
    }

}
