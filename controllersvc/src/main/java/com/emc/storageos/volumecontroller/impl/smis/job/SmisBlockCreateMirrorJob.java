/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;
import java.net.URI;

public class SmisBlockCreateMirrorJob extends SmisBlockMirrorJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisBlockCreateMirrorJob.class);

    public SmisBlockCreateMirrorJob(CIMObjectPath cimJob, URI storageSystem, Boolean wantSyncActive,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "CreateBlockMirror");
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> syncVolumeIter = null;
        DbClient dbClient = jobContext.getDbClient();
        BlockMirrorCreateCompleter completer = null;
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            completer = (BlockMirrorCreateCompleter) getTaskCompleter();
            BlockMirror mirror = dbClient.queryObject(BlockMirror.class, completer.getMirrorURI());
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());

            CIMConnectionFactory cimConnectionFactory;
            WBEMClient client = null;

            // If terminal state update storage pool capacity and remove reservation for mirror capacity
            // from pool's reserved capacity map.
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                cimConnectionFactory = jobContext.getCimConnectionFactory();
                client = getWBEMClient(dbClient, cimConnectionFactory);
                URI poolURI = mirror.getPool();
                SmisUtils.updateStoragePoolCapacity(dbClient, client, poolURI);

                StoragePool pool = dbClient.queryObject(StoragePool.class, poolURI);
                StringMap reservationMap = pool.getReservedCapacityMap();
                // remove from reservation map
                reservationMap.remove(mirror.getId().toString());
                dbClient.persistObject(pool);
            }

            if (jobStatus == JobStatus.SUCCESS) {
                _log.info("Mirror creation success");
                cimConnectionFactory = jobContext.getCimConnectionFactory();
                client = getWBEMClient(dbClient, cimConnectionFactory);
                syncVolumeIter = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
                if (syncVolumeIter.hasNext()) {
                    // Get the target mirror volume native device id
                    CIMObjectPath targetVolumePath = syncVolumeIter.next();
                    CIMInstance syncVolume = client.getInstance(targetVolumePath, false, false, null);

                    String syncDeviceID = targetVolumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
                    String elementName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_ELEMENT_NAME);
                    String wwn = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_WWN_NAME);
                    String alternateName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_NAME);
                    CIMInstance syncInstance = getStorageSyncInstanceFromVolume(client, targetVolumePath);
                    // Lookup the associated source volume based on the volume native device id
                    mirror.setProvisionedCapacity(getProvisionedCapacityInformation(client, syncVolume));
                    mirror.setAllocatedCapacity(getAllocatedCapacityInformation(client, syncVolume));
                    mirror.setWWN(wwn);
                    mirror.setAlternateName(alternateName);
                    mirror.setNativeId(syncDeviceID);
                    mirror.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, mirror));
                    mirror.setDeviceLabel(elementName);
                    mirror.setInactive(false);
                    mirror.setSynchronizedInstance(syncInstance.getObjectPath().toString());
                    updateSynchronizationAspects(client, mirror);
                    // mirror.setIsSyncActive(_wantSyncActive);

                    Volume volume = dbClient.queryObject(Volume.class, mirror.getSource().getURI());
                    _log.info(String
                            .format("For target mirror volume %1$s, going to set BlockMirror %2$s nativeId to %3$s (%4$s). Associated volume is %5$s (%6$s)",
                                    targetVolumePath.toString(), mirror.getId().toString(),
                                    syncDeviceID, elementName, volume.getNativeId(), volume.getDeviceLabel()));
                    dbClient.persistObject(mirror);
                }
            } else if (isJobInTerminalFailedState()) {
                _log.info("Failed to create mirror");
                completer.error(dbClient, DeviceControllerException.exceptions.attachVolumeMirrorFailed(getMessage()));
                dbClient.persistObject(mirror);
            }
        } catch (Exception e) {
            setFatalErrorStatus("Encountered an internal error during block create mirror job status processing: " +
                    e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisBlockCreateMirrorJob", e);
            if (completer != null) {
                completer.error(dbClient, DeviceControllerException.errors.jobFailed(e));
            }
        } finally {
            if (syncVolumeIter != null) {
                syncVolumeIter.close();
            }
            super.updateStatus(jobContext);
        }
    }
}
